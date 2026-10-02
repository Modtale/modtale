package net.modtale.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class PublicCreatorCacheServiceTest {
  @Test
  void unchangedOrHiddenOAuthConnectionsDoNotTriggerBroadInvalidation() {
    User user = new User();
    user.setConnectedAccounts(
        java.util.List.of(
            new User.ConnectedAccount(
                OAuthProvider.GITHUB, "id", "name", "https://github.com/name", true)));
    assertFalse(
        PublicCreatorCacheService.publicConnectionWouldChange(
            user, OAuthProvider.GITHUB, "id", "name", "https://github.com/name", true));
    assertTrue(
        PublicCreatorCacheService.publicConnectionWouldChange(
            user, OAuthProvider.GITHUB, "id", "renamed", "https://github.com/renamed", true));
    assertTrue(
        PublicCreatorCacheService.publicConnectionWouldChange(
            user, OAuthProvider.GITHUB, "id", "name", "https://github.com/name", false));
    user.setConnectedAccounts(
        java.util.List.of(
            new User.ConnectedAccount(
                OAuthProvider.GITHUB, "id", "name", "https://github.com/name", false)));
    assertFalse(
        PublicCreatorCacheService.publicConnectionWouldChange(
            user, OAuthProvider.GITHUB, "new-id", "renamed", "https://github.com/renamed", false));
  }

  @Test
  void creatorChangesEvictEmbeddedPublicCreatorFieldsWithoutChangingAuthorityCaches() {
    var manager =
        new ConcurrentMapCacheManager(
            "projectPageDtos",
            "projectTeamDtos",
            "wikiPageBundleJson",
            "projectSummarySearch",
            "projectPermissionSnapshots",
            "analyticsDebounce");
    for (String name : manager.getCacheNames()) manager.getCache(name).put("test", "cached");
    var invalidator = mock(PublicContentCacheInvalidator.class);
    new PublicCreatorCacheService(manager, invalidator).creatorChanged();
    for (String name :
        java.util.List.of(
            "projectPageDtos", "projectTeamDtos", "wikiPageBundleJson", "projectSummarySearch")) {
      assertNull(manager.getCache(name).get("test"));
    }
    assertNotNull(manager.getCache("projectPermissionSnapshots").get("test"));
    assertNotNull(manager.getCache("analyticsDebounce").get("test"));
    verify(invalidator).contentChanged();
  }
}
