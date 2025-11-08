package ie.ul.cs4297.indexer.svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EsClient {
    private final RestClient http;
    private final String index;
    private final ObjectMapper mapper = new ObjectMapper();

    public EsClient(
            @Value("${app.es-host}") String esHost,
            @Value("${app.es-index}") String index
    ) {
        this.http = RestClient.builder().baseUrl(esHost).build();
        this.index = index;
        ensureIndex();
    }

    private void ensureIndex() {
        try {
            // HEAD /{index}: exists?
            var headResp = http.head().uri("/" + index).retrieve().toEntity(Void.class);
            if (headResp.getStatusCode().is2xxSuccessful()) return;
        } catch (RestClientResponseException e) {
            // 404 means create it
            if (e.getStatusCode().value() != 404) {
                System.err.println("[Indexer] HEAD index failed: " + e.getStatusText());
            }
        }

        // Create index with a simple mapping
        var mapping = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "title":   { "type": "text" },
                  "content": { "type": "text" },
                  "tags":    { "type": "keyword" },
                  "source_url": { "type": "keyword" }
                }
              }
            }
            """;

        try {
            ResponseEntity<String> resp = http.put()
                    .uri("/" + index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapping)
                    .retrieve()
                    .toEntity(String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                System.err.println("[Indexer] Create index failed: HTTP " + resp.getStatusCode());
                if (resp.getBody() != null) System.err.println(resp.getBody());
            } else {
                System.out.println("[Indexer] Created index: " + index);
            }
        } catch (Exception e) {
            System.err.println("[Indexer] Error creating index: " + e.getMessage());
        }
    }

    /** Send prebuilt NDJSON bulk body to ES */
    public void bulkNdjson(String ndjson) {
        try {
            var resp = http.post()
                    .uri("/_bulk")
                    .contentType(MediaType.valueOf("application/x-ndjson"))
                    .header("Accept", "application/json")
                    .body(ndjson)
                    .retrieve()
                    .toEntity(String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                System.err.println("[Indexer] Bulk failed: HTTP " + resp.getStatusCode());
                if (resp.getBody() != null) System.err.println("[Indexer] Bulk body: " + resp.getBody());
            }
        } catch (RestClientResponseException e) {
            System.err.println("[Indexer] Bulk HTTP error: " + e.getStatusCode() + " " + e.getStatusText());
            System.err.println(e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[Indexer] Bulk error: " + e.getMessage());
        }
    }

    public String indexName() { return index; }
}
