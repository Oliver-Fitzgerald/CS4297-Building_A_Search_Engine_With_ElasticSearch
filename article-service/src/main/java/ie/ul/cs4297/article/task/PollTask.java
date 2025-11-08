package ie.ul.cs4297.article.task;

import ie.ul.cs4297.article.service.ArticlePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class PollTask {

    private final ArticlePublisher publisher;

    public PollTask(ArticlePublisher publisher,
                    @Value("${app.poll-interval-ms:5000}") long intervalMs) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.poll-interval-ms:5000}")
    public void tick() {
        publisher.publishNew();
    }
}
