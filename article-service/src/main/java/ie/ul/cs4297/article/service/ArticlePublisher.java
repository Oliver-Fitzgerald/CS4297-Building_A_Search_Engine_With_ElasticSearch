package ie.ul.cs4297.article.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ie.ul.cs4297.article.model.Article;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ArticlePublisher {

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String topic;
    private final AtomicLong lastSeenId = new AtomicLong(0);

    public ArticlePublisher(
            JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            @Value("${app.kafka-topic}") String topic
    ) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;

        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM articles", Long.class);
        lastSeenId.set(max == null ? 0 : max);
        System.out.println("[ArticleService] starting at lastSeenId=" + lastSeenId.get());
    }

    public int publishNew() {
        long fromId = lastSeenId.get();
        List<Article> rows = jdbc.query(
                "SELECT id,title,content,tags,source_url FROM articles WHERE id > ? ORDER BY id ASC LIMIT 500",
                (rs, i) -> new Article(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("tags"),
                        rs.getString("source_url")
                ),
                fromId
        );
        int count = 0;
        for (Article a : rows) {
            try {
                String json = mapper.writeValueAsString(a);
                kafka.send(topic, String.valueOf(a.id()), json);
                lastSeenId.set(a.id());
                count++;
            } catch (Exception e) {
                System.err.println("[ArticleService] publish error id=" + a.id() + " -> " + e.getMessage());
            }
        }
        if (count > 0) System.out.println("[ArticleService] published " + count + " new articles (to " + topic + ")");
        return count;
    }
}
