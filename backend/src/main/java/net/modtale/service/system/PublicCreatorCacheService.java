package net.modtale.service.system;

import java.util.List;
import java.util.Objects;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** Public creator fields are embedded in cached project/team DTOs as well as creator pages. */
@Service
public class PublicCreatorCacheService {
  private final CacheManager cacheManager;
  private final PublicContentCacheInvalidator invalidator;

  public PublicCreatorCacheService(
      CacheManager cacheManager, PublicContentCacheInvalidator invalidator) {
    this.cacheManager = cacheManager;
    this.invalidator = invalidator;
  }

  public void creatorChanged() {
    for (String name :
        List.of(
            "projectDetails",
            "projectDetailDtos",
            "projectPageDtos",
            "projectVersionDtos",
            "projectCommentDtos",
            "projectGalleryDtos",
            "projectTeamDtos",
            "projectMetaDtos",
            "projectSearch",
            "projectSummarySearch",
            "projectMarqueeSearch",
            "projectMarqueeSummarySearch",
            "wikiPageBundleJson",
            "sitemapData")) {
      Cache cache = cacheManager.getCache(name);
      if (cache != null) cache.clear();
    }
    invalidator.contentChanged();
  }

  /** Token refreshes and unchanged sign-ins must not continuously evict the public site. */
  public static boolean publicConnectionWouldChange(
      User user,
      OAuthProvider provider,
      String providerId,
      String username,
      String profileUrl,
      boolean visible) {
    var existing =
        user.getConnectedAccounts() == null
            ? null
            : user.getConnectedAccounts().stream()
                .filter(account -> account.getProvider() == provider)
                .findFirst()
                .orElse(null);
    if (existing == null) return visible;
    if (existing.isVisible() != visible) return true;
    return visible
        && (!Objects.equals(existing.getProviderId(), providerId)
            || !Objects.equals(existing.getUsername(), username)
            || !Objects.equals(existing.getProfileUrl(), profileUrl));
  }
}
