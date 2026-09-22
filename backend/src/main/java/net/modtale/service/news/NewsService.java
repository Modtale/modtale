package net.modtale.service.news;

import java.time.Instant;
import java.util.*;
import net.modtale.model.news.*;
import net.modtale.repository.news.NewsRepository;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsService {
    private final NewsRepository repository;
    public NewsService(NewsRepository repository) { this.repository = repository; }
    private static final PolicyFactory HTML = new HtmlPolicyBuilder()
        .allowElements("p", "br", "h1", "h2", "h3", "h4", "h5", "h6", "strong", "b", "em", "i", "u", "s", "del", "sub", "sup", "mark", "span", "div", "section", "blockquote", "ul", "ol", "li", "hr", "pre", "code", "table", "thead", "tbody", "tr", "td", "th", "a", "img", "video")
        .allowAttributes("href").onElements("a")
        .allowAttributes("id").matching(java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*")).onElements("h1", "h2", "h3", "h4", "h5", "h6")
        .allowAttributes("src", "alt", "title").onElements("img")
        .allowAttributes("src", "poster", "controls", "loop", "muted", "playsinline", "title").onElements("video")
        .allowAttributes("colspan", "rowspan").matching(java.util.regex.Pattern.compile("[1-9][0-9]?" )).onElements("td", "th")
        .allowAttributes("data-demo-clip", "data-demo-alt").onElements("div")
        .allowWithoutAttributes("span", "div")
        .allowUrlProtocols("https", "http", "mailto")
        .allowStyling().toFactory();
    public static String sanitize(String html) { return HTML.sanitize(html); }
    public List<NewsArticle> list() {
        return repository.findAll().stream().sorted(Comparator.comparing((NewsArticle p) -> Objects.toString(p.draftUpdatedAt, "")).reversed()).toList();
    }
    public NewsArticle get(String slug) { return repository.findById(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private void revision(NewsArticle post, Long version) {
        if (!Objects.equals(post.version, version)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This post changed in another editor. Reload it before saving.");
    }
    private NewsArticle persist(NewsArticle post) {
        try { return repository.save(post); }
        catch (OptimisticLockingFailureException | org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "This post changed in another editor. Reload it before saving."); }
    }
    private String media(String value) {
        if (value == null || value.isBlank()) return "";
        if (!(value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) && !value.matches("https?://[^\\s]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an HTTPS URL or site path for media.");
        return value;
    }
    public NewsArticle save(String slug, Long version, NewsContent input) {
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a short URL containing lowercase letters, numbers and hyphens.");
        NewsArticle post = repository.findById(slug).orElseGet(() -> { var p = new NewsArticle(); p.slug = slug; return p; });
        revision(post, version);
        post.draft = new NewsContent(input.title().trim(), input.description(), input.excerpt(), input.author().trim(), List.copyOf(input.tags()), media(input.heroImage()), input.heroAlt(), sanitize(input.body()));
        post.draftUpdatedAt = Instant.now().toString();
        return persist(post);
    }
    public NewsArticle publish(String slug, Long version) {
        NewsArticle post = get(slug); revision(post, version);
        if (post.draft.body().isBlank() || post.draft.body().replaceAll("<[^>]+>", "").isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write some article text before publishing.");
        if (post.draft.description() == null || post.draft.description().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a summary before publishing.");
        if (post.draft.heroImage() == null || post.draft.heroImage().isBlank() || post.draft.heroAlt() == null || post.draft.heroAlt().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a cover image and its description before publishing.");
        post.published = post.draft;
        if (post.publishedAt == null) post.publishedAt = Instant.now().toString();
        post.updatedAt = Instant.now().toString();
        return persist(post);
    }
    public NewsArticle unpublish(String slug, Long version) {
        NewsArticle post = get(slug); revision(post, version); post.published = null; return persist(post);
    }
    public record PublicPost(String slug, String title, String description, String excerpt, String author, List<String> tags, String heroImage, String heroAlt, String socialImage, String socialImageAlt, String body, String publishedAt, String updatedAt, String readingTime) {}
    public PublicPost publicPost(NewsArticle post) {
        var c = post.published;
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        int minutes = Math.max(1, (int)Math.ceil(c.body().replaceAll("<[^>]+>", " ").trim().split("\\s+").length / 220.0));
        return new PublicPost(post.slug, c.title(), c.description(), c.excerpt(), c.author(), c.tags(), c.heroImage(), c.heroAlt(), c.heroImage(), c.heroAlt(), c.body(), post.publishedAt, post.updatedAt, minutes + " min read");
    }
}
