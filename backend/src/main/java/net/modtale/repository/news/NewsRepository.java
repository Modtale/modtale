package net.modtale.repository.news;
import net.modtale.model.news.NewsArticle;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface NewsRepository extends MongoRepository<NewsArticle, String> {}
