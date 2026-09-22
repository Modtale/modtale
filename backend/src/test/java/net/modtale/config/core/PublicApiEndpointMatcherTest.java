package net.modtale.config.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublicApiEndpointMatcherTest {

    @Test
    void newsReadIsPublicButAdminAndWritesAreNot() {
        assertTrue(PublicApiEndpointMatcher.isPublicOperation("/api/v1/news", "GET"));
        assertTrue(PublicApiEndpointMatcher.isPublicOperation("/api/v1/news/example", "HEAD"));
        assertFalse(PublicApiEndpointMatcher.isPublicOperation("/api/v1/admin/news", "GET"));
        assertFalse(PublicApiEndpointMatcher.isPublicOperation("/api/v1/news/example", "PUT"));
    }

    @Test
    void artifactIdentificationIsPublicReadOnlyFunctionality() {
        assertTrue(PublicApiEndpointMatcher.isPublicOperation(
                "/api/v1/projects/external/identify",
                "POST"
        ));
    }

    @Test
    void accountBackedProjectAndListWritesRemainPrivate() {
        assertFalse(PublicApiEndpointMatcher.isPublicOperation(
                "/api/v1/projects/project-1/favorite",
                "POST"
        ));
        assertFalse(PublicApiEndpointMatcher.isPublicOperation(
                "/api/v1/lists",
                "POST"
        ));
    }
}
