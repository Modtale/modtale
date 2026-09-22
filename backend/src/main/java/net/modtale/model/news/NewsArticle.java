package net.modtale.model.news;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("news_articles")
public class NewsArticle {
    @Id public String slug;
    @Version public Long version;
    public NewsContent draft;
    public NewsContent published;
    public String publishedAt;
    public String updatedAt;
    public String draftUpdatedAt;
}
