package ie.ul.cs4297.indexer.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ie.ul.cs4297.indexer.model.Article;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndexerConsumer {

    private final EsClient es;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int bulkSize;

    private final List<Article> buffer = new ArrayList<>();

    public IndexerConsumer(EsClient es, @Value("${app.bulk-size:200}") int bulkSize) {
        this.es = es;
        this.bulkSize = bulkSize;
    }

    @KafkaListener(topics = "${app.kafka-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // Event is a JSON object produced by article-service
            JsonNode root = mapper.readTree(record.value());

            // Accept both flat events and nested "article" payloads
            JsonNode node = root.has("article") ? root.get("article") : root;

            long id         = node.path("id").asLong();
            String title    = nullIfBlank(node.path("title").asText(null));
            String content  = nullIfBlank(node.path("content").asText(null));
            String tags     = nullIfBlank(node.path("tags").asText(null));
            String source   = nullIfBlank(node.path("source_url").asText(null));

            buffer.add(new Article(id, title, content, tags, source));

            if (buffer.size() >= bulkSize) {
                flush();
            }
        } catch (Exception e) {
            System.err.println("[Indexer] Failed to parse/queue record: " + e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() { flush(); }

    private void flush() {
        if (buffer.isEmpty()) return;

        String index = es.indexName();
        StringBuilder nd = new StringBuilder(buffer.size() * 256);

        for (Article a : buffer) {
            // action/metadata line — use MySQL id as ES _id to avoid duplicates
            nd.append("{\"index\":{\"_index\":\"")
                    .append(index)
                    .append("\",\"_id\":\"")
                    .append(a.id())
                    .append("\"}}\n");

            // source line
            nd.append("{")
                    .append("\"id\":").append(a.id()).append(",")
                    .append("\"title\":").append(toJson(a.title())).append(",")
                    .append("\"content\":").append(toJson(a.content())).append(",")
                    .append("\"tags\":").append(toJson(a.tags())).append(",")
                    .append("\"source_url\":").append(toJson(a.source_url()))
                    .append("}\n");
        }

        es.bulkNdjson(nd.toString());
        System.out.println("[Indexer] bulk indexed " + buffer.size() + " docs");
        buffer.clear();
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String toJson(String s) {
        if (s == null) return "null";
        try { return mapper.writeValueAsString(s); }
        catch (Exception e) { return "null"; }
    }
}
