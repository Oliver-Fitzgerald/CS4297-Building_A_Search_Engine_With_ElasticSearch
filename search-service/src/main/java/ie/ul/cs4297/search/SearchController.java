package ie.ul.cs4297.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin // allow a future frontend
public class SearchController {

    private final EsClient es;
    private final int defaultSize;

    @Autowired
    private JdbcTemplate jdbc;

    public SearchController(EsClient es, @Value("${app.default-size:10}") int defaultSize) {
        this.es = es;
        this.defaultSize = defaultSize;
    }

    // --- ElasticSearch relevance search (existing) ---
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public String search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "from", defaultValue = "0") int from,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        int s = (size == null || size < 1 || size > 100) ? defaultSize : size;
        return es.searchJson(q, from, s);
    }

    // Also expose the lab's expected path: /api/search
    @GetMapping(value = "/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public String searchApi(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "from", defaultValue = "0") int from,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return search(q, from, size);
    }

    // --- Phase 3b: Analytics - top tags (Terms aggregation) ---
    @GetMapping(value = "/api/analytics/tags", produces = MediaType.APPLICATION_JSON_VALUE)
    public String topTags() {
        String body = """
        {
          "size": 0,
          "aggs": {
            "top_tags": {
              "terms": { "field": "tags", "size": 10 }
            }
          }
        }
        """;

        return es.http().post()
                .uri("/" + es.indexName() + "/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    // --- Phase 4: Slow MySQL baseline (LIKE %...%) ---
    @GetMapping(value = "/api/search/db", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> slowMySQLSearch(@RequestParam("q") String q) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, title, content FROM articles WHERE content LIKE CONCAT('%', ?, '%')", q
        );
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[Baseline] MySQL search took " + elapsed + " ms, found " + rows.size() + " rows");
        return rows;
    }
}
