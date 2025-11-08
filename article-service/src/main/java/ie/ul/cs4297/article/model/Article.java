package ie.ul.cs4297.article.model;

public record Article(
        long id,
        String title,
        String content,
        String tags,
        String source_url
) {}
