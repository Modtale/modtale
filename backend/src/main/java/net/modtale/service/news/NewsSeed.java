package net.modtale.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.model.news.NewsArticle;
import net.modtale.repository.news.NewsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class NewsSeed implements ApplicationRunner {
    private final NewsRepository repository;
    public NewsSeed(NewsRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) throws Exception {
        try (var input = new ClassPathResource("news-seed.json").getInputStream()) {
            for (var post : new ObjectMapper().readValue(input, NewsArticle[].class)) {
                if (repository.existsById(post.slug)) continue;
                try { repository.insert(post); } catch (DuplicateKeyException ignored) { /* Another instance imported it. */ }
            }
        }
    }
}
