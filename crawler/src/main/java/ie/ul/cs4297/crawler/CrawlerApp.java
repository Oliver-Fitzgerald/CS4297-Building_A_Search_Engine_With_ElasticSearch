package ie.ul.cs4297.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class CrawlerApp {

    private static final String JDBC_URL =
            "jdbc:mysql://127.0.0.1:3307/cs4297?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String JDBC_USER = "cs4297";
    private static final String JDBC_PASS = "cs4297";

    private static final List<String> SEEDS = List.of(
            "https://books.toscrape.com/catalogue/page-1.html"
    );

    private static final int SLEEP_MS    = 5000;  // ethics: 5s/request
    private static final int MAX_INSERTS = 2;   // small cap while testing

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS)) {
            conn.setAutoCommit(false);

            ArrayDeque<String> queue = new ArrayDeque<>(SEEDS);
            HashSet<String> visited = new HashSet<>();
            int inserted = 0;

            while (!queue.isEmpty() && inserted < MAX_INSERTS) {
                String url = queue.poll();
                if (url == null || visited.contains(url)) continue;
                if (!url.startsWith("https://books.toscrape.com/")) continue;
                visited.add(url);

                safeSleep(SLEEP_MS);

                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (compatible; CS4297 Crawler; +https://example.edu)")
                            .referrer("https://www.google.com/")
                            .timeout(25000)
                            .get();

                    // Correct URL classification
                    boolean isListing = url.contains("/catalogue/") && url.contains("/page-") && url.endsWith(".html");
                    boolean isDetail  = url.contains("/catalogue/") && url.endsWith("/index.html");

                    if (isDetail) {
                        // --------- Extract from a book detail page ----------
                        String title = selText(doc, "div.product_main h1");
                        if (title.isBlank()) title = doc.title();

                        // Description: the paragraph right after #product_description
                        String description = "";
                        Element descHeader = doc.selectFirst("#product_description");
                        if (descHeader != null) {
                            Element p = descHeader.parent().selectFirst("#product_description + p");
                            // If the direct sibling query fails (older jsoup), try manual sibling walk:
                            if (p == null) {
                                Element sibling = descHeader.nextElementSibling();
                                if (sibling != null && sibling.tagName().equalsIgnoreCase("p")) {
                                    p = sibling;
                                }
                            }
                            if (p != null) description = p.text();
                        }

                        // Fallback if no explicit description: price + availability
                        if (description.isBlank()) {
                            String price = selText(doc, "p.price_color");
                            String avail = selText(doc, "p.availability");
                            description = (price + " " + avail).trim();
                        }

                        // Tags: category + star rating
                        String category = selText(doc, "ul.breadcrumb li:nth-child(3) a");
                        String rating = "";
                        Element ratingEl = doc.selectFirst("p.star-rating");
                        if (ratingEl != null) {
                            String[] parts = ratingEl.className().split("\\s+"); // e.g., "star-rating Three"
                            if (parts.length >= 2) rating = parts[1];
                        }

                        String tags = List.of(category, rating).stream()
                                .filter(s -> s != null && !s.isBlank())
                                .limit(10)
                                .collect(Collectors.joining(";"));

                        if (isNonEmpty(title) && isNonEmpty(description)) {
                            insertArticle(conn, title, description, tags, url);
                            conn.commit();
                            inserted++;
                            System.out.println("[CRAWLED] (" + inserted + "/" + MAX_INSERTS + ") " + title);
                        } else {
                            conn.rollback();
                            System.out.println("[SKIP] " + url + " (missing title/content)");
                        }

                    } else if (isListing) {
                        // --------- Discover detail links on a listing page ----------
                        for (Element a : doc.select("article.product_pod h3 a[href]")) {
                            String href = absolutize(url, a.attr("href"));
                            if (href != null && href.startsWith("https://books.toscrape.com/") && href.endsWith("/index.html")) {
                                queue.offer(href);
                            }
                        }

                        // Next page link
                        Element next = doc.selectFirst("li.next a[href]");
                        if (next != null) {
                            String nextUrl = absolutize(url, next.attr("href"));
                            if (nextUrl != null && nextUrl.startsWith("https://books.toscrape.com/")) {
                                queue.offer(nextUrl);
                            }
                        }

                        System.out.println("[LIST] queued more links from " + url);
                    } else {
                        // ignore non-matching pages (e.g., base home)
                    }

                } catch (Exception ex) {
                    conn.rollback();
                    System.err.println("[ERROR] " + url + " -> " + ex.getMessage());
                }
            }
        }
    }

    // ---------- Helpers ----------

    private static String selText(Document d, String css) {
        Element el = d.selectFirst(css);
        return (el == null) ? "" : el.text();
    }

    private static void insertArticle(Connection conn, String title, String content, String tags, String url) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO articles(title, content, tags, source_url) VALUES (?,?,?,?)")) {
            ps.setString(1, title);
            ps.setString(2, content);
            ps.setString(3, tags);
            ps.setString(4, url);
            ps.executeUpdate();
        }
    }

    private static boolean isNonEmpty(String s) {
        return s != null && !s.isBlank();
    }

    private static void safeSleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static String absolutize(String baseUrl, String href) {
        try { return new java.net.URI(baseUrl).resolve(href).toString(); }
        catch (Exception e) { return null; }
    }
}
