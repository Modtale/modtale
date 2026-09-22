package net.modtale.launcher.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.modtale.launcher.api.ModtaleApiClient;
import org.junit.jupiter.api.Test;

class LauncherConfigTest {

    @Test
    void defaultsToProductionSite() {
        assertEquals(ModtaleApiClient.DEFAULT_SITE_BASE_URL, LauncherConfig.normalizeSiteBaseUrl(null));
    }

    @Test
    void defaultsToProductionApi() {
        assertEquals(ModtaleApiClient.DEFAULT_API_BASE_URL, LauncherConfig.normalizeApiBaseUrl(null));
    }

    @Test
    void defaultsToModtaleLauncherUpdateRepository() {
        assertEquals("Modtale/modtale", LauncherConfig.normalizeLauncherUpdatesRepository(null));
    }

    @Test
    void acceptsOwnerRepoLauncherUpdateRepository() {
        assertEquals("Example/project.launcher", LauncherConfig.normalizeLauncherUpdatesRepository("Example/project.launcher"));
    }

    @Test
    void acceptsNumericDiscordClientId() {
        assertEquals("123456789012345678", LauncherConfig.normalizeDiscordClientId(" 123456789012345678 ").orElseThrow());
    }

    @Test
    void ignoresMissingOrDevelopmentDiscordClientId() {
        assertTrue(LauncherConfig.normalizeDiscordClientId(null).isEmpty());
        assertTrue(LauncherConfig.normalizeDiscordClientId("dev").isEmpty());
        assertTrue(LauncherConfig.normalizeDiscordClientId("not-a-client-id").isEmpty());
    }

    @Test
    void normalizesLocalhostWithoutSchemeForDevelopment() {
        assertEquals("http://localhost:5173", LauncherConfig.normalizeSiteBaseUrl("localhost:5173/"));
    }

    @Test
    void normalizesProductionHostWithoutSchemeToHttps() {
        assertEquals("https://modtale.net", LauncherConfig.normalizeSiteBaseUrl("modtale.net"));
    }

    @Test
    void systemPropertyOverridesDefaultSite() {
        String previous = System.getProperty(LauncherConfig.SITE_BASE_URL_PROPERTY);
        try {
            System.setProperty(LauncherConfig.SITE_BASE_URL_PROPERTY, "localhost:5173");

            assertEquals("http://localhost:5173", LauncherConfig.siteBaseUrl());
        } finally {
            if (previous == null) {
                System.clearProperty(LauncherConfig.SITE_BASE_URL_PROPERTY);
            } else {
                System.setProperty(LauncherConfig.SITE_BASE_URL_PROPERTY, previous);
            }
        }
    }

    @Test
    void systemPropertyOverridesDefaultApi() {
        String previous = System.getProperty(LauncherConfig.API_BASE_URL_PROPERTY);
        try {
            System.setProperty(LauncherConfig.API_BASE_URL_PROPERTY, "localhost:8080/api/v1");

            assertEquals("http://localhost:8080/api/v1", LauncherConfig.apiBaseUrl());
        } finally {
            if (previous == null) {
                System.clearProperty(LauncherConfig.API_BASE_URL_PROPERTY);
            } else {
                System.setProperty(LauncherConfig.API_BASE_URL_PROPERTY, previous);
            }
        }
    }
}
