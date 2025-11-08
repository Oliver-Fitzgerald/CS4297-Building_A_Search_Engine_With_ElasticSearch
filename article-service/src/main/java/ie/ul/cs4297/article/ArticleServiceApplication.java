package ie.ul.cs4297.article;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArticleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArticleServiceApplication.class, args);
    }
}
