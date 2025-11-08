package ie.ul.cs4297.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EsClient {
    private final RestClient http;
    private final String index;

    public EsClient(@Value("${app.es-host}") String esHost,
                    @Value("${app.es-index}") String index) {
        this.http = RestClient.builder().baseUrl(esHost).build();
        this.index = index;
    }

    public String searchJson(String q, int from, int size) {
        // Simple match on title + content, optional tag: filter
        String tags = null;
        String query = (q == null) ? "" : q.trim();
        if (query.startsWith("tag:")) {
            int sp = query.indexOf(' ');
            tags = (sp < 0) ? query.substring(4) : query.substring(4, sp);
            query = (sp < 0) ? "" : query.substring(sp + 1).trim();
        }

        String body = """
        {
          "from": %d, "size": %d,
          "_source": ["id","title","content","tags","source_url"],
          "query": {
            "bool": {
              "must": [
                { "multi_match": {
                    "query": %s,
                    "fields": ["title^2","content"]
                } }
              ],
              "filter": [
                %s
              ]
            }
          },
          "highlight": {
            "fields": {
              "title": {}, "content": {}
            },
            "fragment_size": 120, "number_of_fragments": 1
          }
        }
        """.formatted(
                Math.max(0, from),
                Math.max(1, size),
                jsonStr((query == null || query.isBlank()) ? "*" : query),
                (tags == null ? "" : """
                  { "terms": { "tags": [ %s ] } }
                """.formatted(csv(jsonArraySplit(tags))))
        );

        return http.post()
                .uri("/" + index + "/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    // helpers
    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"") + "\"";
    }
    private static String[] jsonArraySplit(String s) {
        return s.split("[,;]\\s*");
    }
    private static String csv(String[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (a[i].isBlank()) continue;
            if (b.length() > 0) b.append(",");
            b.append(jsonStr(a[i].trim()));
        }
        return b.toString();
    }

    // expose RestClient + index for analytics endpoint
    public RestClient http() { return this.http; }
    public String indexName() { return this.index; }
}
