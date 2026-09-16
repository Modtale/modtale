package net.modtale.controller.news;

import jakarta.validation.Valid;
import java.util.List;
import net.modtale.model.news.*;
import net.modtale.service.news.NewsService;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.admin.audit.AdminAuditLogger;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NewsController {
    private final NewsService service;
    private final AccountService accounts;
    private final AdminAuditLogger audit;
    private final net.modtale.service.storage.StorageService storage;
    private final net.modtale.service.security.validation.ProjectImageValidationService images;
    public NewsController(NewsService service, AccountService accounts, AdminAuditLogger audit, net.modtale.service.storage.StorageService storage, net.modtale.service.security.validation.ProjectImageValidationService images) { this.service=service; this.accounts=accounts; this.audit=audit; this.storage=storage; this.images=images; }
    public record SaveRequest(Long version, @Valid @jakarta.validation.constraints.NotNull NewsContent content) {}
    public record Revision(Long version) {}
    @PostMapping("/admin/news/media")
    @PreAuthorize("@apiSecurity.hasAdminPermission('NEWS_MANAGE', authentication) or @apiSecurity.hasAdminPermission('USER_PERMISSION_MANAGE', authentication)")
    public java.util.Map<String, String> upload(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        accounts.requireCurrentUser("uploading news images");
        images.validateImage(file, "News");
        byte[] bytes = file.getBytes();
        String ext = bytes[0] == (byte)0x89 ? "png" : bytes[0] == (byte)0xff ? "jpg" : bytes[0] == 'G' ? "gif" : bytes[0] == 'R' ? "webp" : "";
        if (ext.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Use PNG, JPEG, WebP or GIF images.");
        String path = "news/" + java.util.UUID.randomUUID() + "." + ext;
        storage.uploadDirect(path, bytes, "image/" + (ext.equals("jpg") ? "jpeg" : ext));
        return java.util.Map.of("url", storage.getPublicUrl(path));
    }
    @GetMapping("/news")
    public List<NewsService.PublicPost> published() { return service.list().stream().filter(p -> p.published != null).map(service::publicPost).sorted(java.util.Comparator.comparing(NewsService.PublicPost::publishedAt).reversed()).toList(); }
    @GetMapping("/news/{slug}")
    public NewsService.PublicPost published(@PathVariable String slug) { return service.publicPost(service.get(slug)); }
    @GetMapping("/admin/news")
    @PreAuthorize("@apiSecurity.hasAdminPermission('NEWS_MANAGE', authentication) or @apiSecurity.hasAdminPermission('USER_PERMISSION_MANAGE', authentication)")
    public List<NewsArticle> list() { return service.list(); }
    @PutMapping("/admin/news/{slug}")
    @PreAuthorize("@apiSecurity.hasAdminPermission('NEWS_MANAGE', authentication) or @apiSecurity.hasAdminPermission('USER_PERMISSION_MANAGE', authentication)")
    public NewsArticle save(@PathVariable String slug, @Valid @RequestBody SaveRequest request) {
        var user = accounts.requireCurrentUser("editing news");
        var result = service.save(slug, request.version(), request.content());
        audit.logAction(user.getId(), "SAVE_NEWS_DRAFT", slug, "NEWS", "Saved news draft"); return result;
    }
    @PostMapping("/admin/news/{slug}/publish")
    @PreAuthorize("@apiSecurity.hasAdminPermission('NEWS_MANAGE', authentication) or @apiSecurity.hasAdminPermission('USER_PERMISSION_MANAGE', authentication)")
    public NewsArticle publish(@PathVariable String slug, @RequestBody Revision request) {
        var user = accounts.requireCurrentUser("publishing news"); var result = service.publish(slug, request.version());
        audit.logAction(user.getId(), "PUBLISH_NEWS", slug, "NEWS", "Published news post"); return result;
    }
    @PostMapping("/admin/news/{slug}/unpublish")
    @PreAuthorize("@apiSecurity.hasAdminPermission('NEWS_MANAGE', authentication) or @apiSecurity.hasAdminPermission('USER_PERMISSION_MANAGE', authentication)")
    public NewsArticle unpublish(@PathVariable String slug, @RequestBody Revision request) {
        var user = accounts.requireCurrentUser("unpublishing news"); var result = service.unpublish(slug, request.version());
        audit.logAction(user.getId(), "UNPUBLISH_NEWS", slug, "NEWS", "Unpublished news post"); return result;
    }
}
