package net.modtale.config.secretbundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.boot.support.EnvironmentPostProcessorsFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SecretBundleEnvironmentPostProcessorTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final List<Path> reads = new ArrayList<>();
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final SecretBundleEnvironmentPostProcessor processor = new SecretBundleEnvironmentPostProcessor(path -> {
        reads.add(path);
        byte[] raw = files.get(path.getFileName().toString());
        if (raw == null) {
            throw new IOException("sensitive-fixture-reader-error");
        }
        return raw;
    });

    @Test
    void disabledAndDefaultLeaveLegacyEnvironmentUnchangedWithoutReading() {
        for (String flag : new String[]{null, "false"}) {
            MockEnvironment environment = new MockEnvironment().withProperty("R2_SECRET_KEY", "legacy");
            if (flag != null) environment.setProperty(SecretBundleEnvironmentPostProcessor.ENABLED, flag);
            processor.postProcessEnvironment(environment, null);
            assertEquals("legacy", environment.getProperty("R2_SECRET_KEY"));
            assertFalse(environment.getPropertySources().contains(SecretBundleEnvironmentPostProcessor.SOURCE_NAME));
        }
        assertTrue(reads.isEmpty());
    }

    @TestFactory
    Stream<DynamicTest> rejectsNonBooleanFeatureFlags() {
        return Stream.of("TRUE", "False", "yes", "1", "", " true ").map(flag -> DynamicTest.dynamicTest("invalid flag: " + flag, () -> {
            MockEnvironment environment = environment("dev");
            environment.setProperty(SecretBundleEnvironmentPostProcessor.ENABLED, flag);
            assertSafeFailure(environment);
            assertTrue(reads.isEmpty());
        }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsUnknownOrMissingProfiles() {
        return Stream.of("", "production", "../../shared", "DEV").map(profile -> DynamicTest.dynamicTest("invalid profile: " + profile, () -> {
            assertSafeFailure(environment(profile));
            assertTrue(reads.isEmpty());
        }));
    }

    @Test
    void rejectsMissingProfile() {
        assertSafeFailure(new MockEnvironment().withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "true"));
    }

    @Test
    void devReadsOnlyFixedSharedFileAndOverridesMappedLegacyValues() throws IOException {
        Map<String, String> secrets = shared();
        secrets.put("R2_SECRET_KEY", "bundle-value");
        bundle("shared", secrets);
        MockEnvironment environment = environment("dev");
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        environment.setProperty("R2_SECRET_KEY", "legacy-value");
        environment.setProperty("app.r2.secret-key", "legacy-canonical-value");
        environment.setProperty("app.r2.bucket", "preserved-bucket");
        processor.postProcessEnvironment(environment, null);
        assertEquals(List.of(Path.of("/app/secrets/bundles/shared.json")), reads);
        assertEquals("bundle-value", environment.getProperty("R2_SECRET_KEY"));
        assertEquals("bundle-value", environment.getProperty("app.r2.secret-key"));
        assertEquals("preserved-bucket", environment.getProperty("app.r2.bucket"));
        assertNull(environment.getProperty("HYTALE_CLIENT_SECRET"));
        assertNull(environment.getProperty("HYTALEMODDING_KEY"));
        assertNull(environment.getProperty("WEBHOOK_URL"));
        assertNull(environment.getProperty("GOOGLE_CLIENT_SECREY"));
        assertEquals("", environment.getProperty("app.public-cache-purge.token"));
    }

    @Test
    void devPreservesBothWebhookConsumersPresentInTheLiveService() {
        bundle("shared", shared());
        MockEnvironment environment = environment("dev");
        processor.postProcessEnvironment(environment, null);
        assertEquals("fixture-DISCORD_WEBHOOK_URL", environment.getProperty("DISCORD_WEBHOOK_URL"));
        assertEquals("fixture-DISCORD_WEBHOOK_URL", environment.getProperty("app.discord-webhook.url"));
        assertEquals("fixture-ADMIN_DISCORD_WEBHOOK_URL", environment.getProperty("ADMIN_DISCORD_WEBHOOK_URL"));
        assertEquals("fixture-ADMIN_DISCORD_WEBHOOK_URL", environment.getProperty("app.admin-discord-webhook.url"));
        assertNull(environment.getProperty("WEBHOOK_URL"));
        assertNull(environment.getProperty("HYTALEMODDING_KEY"));
    }

    @Test
    void newSharedBundlesOmitWardenWhileOriginalBindingRemainsAuthoritative() throws IOException {
        Map<String, String> values = shared();
        values.remove("WARDEN_API_KEY");
        bundle("shared", values);
        MockEnvironment environment = environment("dev").withProperty("WARDEN_API_KEY", "original-only");
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        processor.postProcessEnvironment(environment, null);
        assertEquals("original-only", environment.getProperty("app.warden.api-key"));
        assertNull(environment.getPropertySources().get(SecretBundleEnvironmentPostProcessor.SOURCE_NAME).getProperty("WARDEN_API_KEY"));
    }

    @Test
    void originalWardenBindingWinsOverTheUnusedBundleCopy() throws IOException {
        Map<String, String> values = shared();
        values.put("WARDEN_API_KEY", "unused-bundle-warden-key");
        bundle("shared", values);
        for (String profile : new String[]{"prod", "dev"}) {
            MockEnvironment environment = environment(profile).withProperty("WARDEN_API_KEY", "original-warden-key");
            environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
            processor.postProcessEnvironment(environment, null);
            assertEquals("original-warden-key", environment.getProperty("WARDEN_API_KEY"));
            assertEquals("original-warden-key", environment.getProperty("app.warden.api-key"));
            assertEquals("original-warden-key", Binder.get(environment).bind("app.warden.api-key", String.class).orElseThrow(AssertionError::new));
            var source = environment.getPropertySources().get(SecretBundleEnvironmentPostProcessor.SOURCE_NAME);
            assertNull(source.getProperty("WARDEN_API_KEY"));
            assertNull(source.getProperty("app.warden.api-key"));
        }
    }

    @Test
    void restartSeesRotatedOriginalWardenBindingWithoutChangingBundleVersion() throws IOException {
        bundle("shared", shared());
        for (String original : new String[]{"original-before-rotation", "original-after-rotation"}) {
            MockEnvironment environment = environment("dev").withProperty("WARDEN_API_KEY", original);
            environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
            processor.postProcessEnvironment(environment, null);
            assertEquals(original, environment.getProperty("app.warden.api-key"));
        }
        assertEquals(2, reads.size());
    }

    @Test
    void prodAndDevRequireTheOriginalWardenBindingEvenWithABundleCopy() {
        bundle("shared", shared());
        for (String profile : new String[]{"prod", "dev"}) {
            MockEnvironment missing = new MockEnvironment()
                    .withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "true")
                    .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, profile);
            assertSafeFailure(missing);
            assertSafeFailure(environment(profile).withProperty("WARDEN_API_KEY", ""));
        }
        assertTrue(reads.isEmpty());
    }

    @Test
    void prodReadsOnlySharedAndPreservesOriginalHytaleWithProductionWebhooks() throws IOException {
        bundle("shared", shared());
        // An obsolete production file, even malformed, must never be opened.
        files.put("production.json", new byte[]{1});
        MockEnvironment environment = environment("prod").withProperty("HYTALE_CLIENT_SECRET", "original-hytale-value");
        environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
        processor.postProcessEnvironment(environment, null);
        processor.postProcessEnvironment(environment, null);
        assertEquals(List.of(Path.of("/app/secrets/bundles/shared.json")), reads);
        assertEquals("original-hytale-value", environment.getProperty("HYTALE_CLIENT_SECRET"));
        assertEquals("original-hytale-value", environment.getProperty("spring.security.oauth2.client.registration.hytale.client-secret"));
        assertEquals("original-hytale-value", Binder.get(environment)
                .bind("spring.security.oauth2.client.registration.hytale.client-secret", String.class).orElseThrow(AssertionError::new));
        var source = environment.getPropertySources().get(SecretBundleEnvironmentPostProcessor.SOURCE_NAME);
        assertNull(source.getProperty("HYTALE_CLIENT_SECRET"));
        assertNull(source.getProperty("spring.security.oauth2.client.registration.hytale.client-secret"));
        assertEquals("fixture-ADMIN_DISCORD_WEBHOOK_URL", environment.getProperty("app.admin-discord-webhook.url"));
        assertEquals("fixture-HYTALEMODDING_KEY", environment.getProperty("app.webhook.key"));
        assertEquals("fixture-WEBHOOK_URL", environment.getProperty("app.webhook.url"));
    }

    @Test
    void restartSeesRotatedOriginalHytaleBindingWithoutChangingSharedBundle() throws IOException {
        bundle("shared", shared());
        for (String original : new String[]{"original-before-rotation", "original-after-rotation"}) {
            MockEnvironment environment = environment("prod").withProperty("HYTALE_CLIENT_SECRET", original);
            environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
            processor.postProcessEnvironment(environment, null);
            assertEquals(original, environment.getProperty("spring.security.oauth2.client.registration.hytale.client-secret"));
        }
        assertEquals(List.of(Path.of("/app/secrets/bundles/shared.json"), Path.of("/app/secrets/bundles/shared.json")), reads);
    }

    @Test
    void sharedRejectsHytaleAliasesRatherThanShadowingOriginalBindings() {
        for (String key : new String[]{"HYTALE_CLIENT_SECRET", "spring.security.oauth2.client.registration.hytale.client-secret", "app.warden.api-key"}) {
            Map<String, String> secrets = shared();
            secrets.put(key, "sensitive-fixture-stale-alias");
            bundle("shared", secrets);
            for (String profile : new String[]{"prod", "dev"}) {
                assertSafeFailure(environment(profile));
            }
        }
    }

    @Test
    void devAndPreviewsNeitherRequireNorGainHytaleCredentials() {
        bundle("shared", shared());
        bundle("branch-preview", preview("branch-preview", "feature"));
        bundle("pr-preview", preview("pr-preview", "42"));
        bundle("production", Map.of("HYTALE_CLIENT_SECRET", "sensitive-fixture-obsolete-hytale"));
        for (String profile : new String[]{"dev", "branch-preview", "pr-preview"}) {
            MockEnvironment environment = environment(profile);
            if (!profile.equals("dev")) {
                environment.setProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, profile.equals("branch-preview") ? "feature" : "42");
            }
            processor.postProcessEnvironment(environment, null);
            assertNull(environment.getProperty("HYTALE_CLIENT_SECRET"));
            assertNull(environment.getProperty("spring.security.oauth2.client.registration.hytale.client-secret"));
        }
        assertFalse(reads.contains(Path.of("/app/secrets/bundles/production.json")));
    }

    @Test
    void disabledProdDoesNotRequireOriginalHytaleOrWarden() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "false")
                .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, "prod")
                .withProperty("HYTALE_CLIENT_SECRET", "")
                .withProperty("WARDEN_API_KEY", "");
        processor.postProcessEnvironment(environment, null);
        assertTrue(reads.isEmpty());
        assertFalse(environment.getPropertySources().contains(SecretBundleEnvironmentPostProcessor.SOURCE_NAME));
    }

    @Test
    void optionalCacheTokenMapsItsRuntimeAlias() {
        Map<String, String> shared = shared();
        shared.put("MODTALE_PUBLIC_CACHE_PURGE_TOKEN", "purge-token");
        bundle("shared", shared);
        MockEnvironment environment = environment("dev");
        processor.postProcessEnvironment(environment, null);
        assertEquals("purge-token", environment.getProperty("CLOUDFLARE_CACHE_PURGE_TOKEN"));
        assertEquals("purge-token", environment.getProperty("app.public-cache-purge.token"));
    }

    @Test
    void enabledCachePurgeRequiresItsOptionalToken() {
        bundle("shared", shared());
        assertSafeFailure(environment("dev").withProperty("PUBLIC_CACHE_PURGE_ENABLED", "true"));
        assertSafeFailure(environment("dev").withProperty("app.public-cache-purge.enabled", "true"));
    }

    @Test
    void nonPreviewProfileRejectsUnexpectedPreviewIdentifier() {
        assertSafeFailure(environment("prod").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, "feature"));
        assertSafeFailure(environment("dev").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, "42"));
        assertTrue(reads.isEmpty());
    }

    @Test
    void branchSelectsOnlyItsCredentialsAndTemplateSource() {
        Map<String, String> secrets = preview("branch-preview", "feature-a");
        secrets.putAll(dynamic("branch-preview", "feature-b"));
        bundle("branch-preview", secrets);
        MockEnvironment environment = environment("branch-preview").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, "feature-a");
        processor.postProcessEnvironment(environment, null);
        assertEquals(List.of(Path.of("/app/secrets/bundles/branch-preview.json")), reads);
        assertEquals("fixture-branch-preview-feature-a-r2-secret-key", environment.getProperty("app.r2.secret-key"));
        assertEquals("fixture-R2_TEMPLATE_READ_SECRET_KEY", environment.getProperty("app.seeding.source-r2-secret-key"));
        assertEquals("fixture-BRANCH_PREVIEW_MONGODB_URI", environment.getProperty("spring.mongodb.uri"));
        assertNull(environment.getProperty("branch-preview-feature-b-r2-secret-key"));
        assertNull(environment.getProperty("R2_TEMPLATE_READ_SECRET_KEY"));
        assertNull(environment.getProperty("GITHUB_CLIENT_SECRET"));
    }

    @Test
    void prSelectsOnlyItsDedicatedProjectBundle() {
        bundle("pr-preview", preview("pr-preview", "42"));
        MockEnvironment environment = environment("pr-preview").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, "42");
        processor.postProcessEnvironment(environment, null);
        assertEquals(List.of(Path.of("/app/secrets/bundles/pr-preview.json")), reads);
        assertEquals("fixture-pr-preview-42-r2-access-key", environment.getProperty("R2_ACCESS_KEY"));
        assertEquals("fixture-PREVIEW_SOURCE_R2_SECRET_KEY", environment.getProperty("APP_SEEDING_SOURCE_R2_SECRET_KEY"));
    }

    @TestFactory
    Stream<DynamicTest> rejectsUnsafePreviewIdentifiers() {
        return Stream.of("", "main", "develop", "dev", "UPPER", "a/b", "-edge", "edge-", "a".repeat(21))
                .map(id -> DynamicTest.dynamicTest("invalid branch identifier: " + id, () -> {
                    assertSafeFailure(environment("branch-preview").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, id));
                    assertTrue(reads.isEmpty());
                }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsUnsafePrIdentifiers() {
        return Stream.of("0", "01", "-1", "1/2", "12a").map(id -> DynamicTest.dynamicTest("invalid PR identifier: " + id, () -> {
            assertSafeFailure(environment("pr-preview").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, id));
            assertTrue(reads.isEmpty());
        }));
    }

    @Test
    void missingPreviewCredentialFailsBeforePublishingProperties() {
        Map<String, String> secrets = preview("branch-preview", "feature");
        secrets.remove("branch-preview-feature-r2-token-id");
        bundle("branch-preview", secrets);
        assertSafeFailure(environment("branch-preview").withProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, "feature"));
    }

    @Test
    void missingSharedRequiredKeyFailsClosed() {
        Map<String, String> secrets = shared();
        secrets.remove("R2_SECRET_KEY");
        bundle("shared", secrets);
        assertSafeFailure(environment("dev"));
    }

    @Test
    void fileReadFailureHasNoOriginalCauseOrPayload() {
        assertSafeFailure(environment("dev"));
    }

    @Test
    void prodRequiresOriginalHytaleEvenWithAnObsoleteProductionBundle() {
        bundle("shared", shared());
        bundle("production", Map.of("HYTALE_CLIENT_SECRET", "sensitive-fixture-obsolete-hytale"));
        MockEnvironment missing = new MockEnvironment()
                .withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "true")
                .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, "prod")
                .withProperty("WARDEN_API_KEY", "fixture-original-warden-key");
        assertSafeFailure(missing);
        assertSafeFailure(environment("prod").withProperty("HYTALE_CLIENT_SECRET", ""));
        // A canonical override is not a substitute for the original secret binding.
        missing.setProperty("spring.security.oauth2.client.registration.hytale.client-secret", "sensitive-fixture-canonical");
        assertSafeFailure(missing);
        assertTrue(reads.isEmpty());
    }

    @Test
    void startupSnapshotIsReadOnceAndNotRereadByPropertyConsumers() {
        bundle("shared", shared());
        MockEnvironment environment = environment("dev");
        processor.postProcessEnvironment(environment, null);
        files.put("shared.json", new byte[]{1});
        processor.postProcessEnvironment(environment, null);
        for (int i = 0; i < 5; i++) {
            assertEquals("fixture-R2_SECRET_KEY", environment.getProperty("app.r2.secret-key"));
            assertEquals("fixture-R2_SECRET_KEY", Binder.get(environment).bind("app.r2.secret-key", String.class).orElseThrow(AssertionError::new));
        }
        assertEquals(1, reads.size());
    }

    @TestFactory
    Stream<DynamicTest> invalidPayloadsAreStrictAndNeverDiscloseData() {
        String valid = new String(encoded("shared", shared()), StandardCharsets.UTF_8);
        return Stream.of(
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                valid.replace("\"R2_SECRET_KEY\":", "\"R2_SECRET_KEY\":\"sensitive-fixture-duplicate\",\"R2_SECRET_KEY\":"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":true"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"boundary\":\"shared\"", "\"boundary\":\"production\""),
                valid.replace("\"boundary\":", "\"extra\":\"sensitive-fixture-extra\",\"boundary\":"),
                valid.replace("\"R2_SECRET_KEY\"", "\"sensitive-fixture-unknown-key\""),
                valid.replace("fixture-R2_SECRET_KEY", ""),
                valid.replace("fixture-R2_SECRET_KEY", "\\u0000"),
                valid.replace("fixture-R2_SECRET_KEY", "\\ud800"),
                valid.replace("fixture-R2_SECRET_KEY", "\\udc00"),
                valid.replace("\"fixture-R2_SECRET_KEY\"", "123"),
                valid.replace("\"fixture-R2_SECRET_KEY\"", "null"),
                valid + " {}", "{sensitive-fixture-broken", "[]", "null")
                .map(payload -> DynamicTest.dynamicTest("strict invalid payload", () -> {
                    files.put("shared.json", payload.getBytes(StandardCharsets.UTF_8));
                    assertSafeFailure(environment("dev"));
                }));
    }

    @Test
    void rejectsOversizedAndInvalidUtf8Payloads() {
        files.put("shared.json", new byte[SecretBundleEnvironmentPostProcessor.MAX_BYTES + 1]);
        assertSafeFailure(environment("dev"));
        files.put("shared.json", new byte[]{(byte) 0xc3, (byte) 0x28});
        assertSafeFailure(environment("dev"));
    }

    @TestFactory
    Stream<DynamicTest> literalCredentialsSurviveRealSpringEnvironmentBinderAndValueResolution() {
        return Stream.of("${RESOLVABLE}", "prefix-${MISSING:default}-suffix", "\\${RESOLVABLE}", "\\\\${RESOLVABLE}",
                        "${outer:${RESOLVABLE}}", "${incomplete", "${outer${RESOLVABLE}", "${with{braces}}", "${}",
                        "ordinary\\path\\value", "line1\nline2\r\t'\"$!;", "${a}:${b}\\${c}", "🙂${RESOLVABLE}")
                .map(literal -> DynamicTest.dynamicTest("literal round trip", () -> {
                    Map<String, String> secrets = shared();
                    secrets.put("R2_SECRET_KEY", literal);
                    bundle("shared", secrets);
                    MockEnvironment environment = environment("dev").withProperty("RESOLVABLE", "MUST-NOT-REPLACE");
                    environment.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));
                    processor.postProcessEnvironment(environment, null);
                    assertEquals(literal, environment.getProperty("R2_SECRET_KEY"));
                    assertEquals(literal, environment.getProperty("app.r2.secret-key"));
                    assertEquals(literal, environment.resolveRequiredPlaceholders("${app.r2.secret-key}"));
                    assertEquals(literal, Binder.get(environment).bind("app.r2.secret-key", String.class).orElseThrow(AssertionError::new));
                    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                        context.setEnvironment(environment);
                        context.register(ValueConfiguration.class);
                        context.refresh();
                        assertEquals(literal, context.getBean(ValueBean.class).value);
                    }
                }));
    }

    @Test
    void bootFourFactoryRegistrationLoadsAdapter() {
        assertTrue(EnvironmentPostProcessorsFactory.fromSpringFactories(getClass().getClassLoader())
                .getEnvironmentPostProcessors(new DeferredLogs(), new DefaultBootstrapContext()).stream()
                .anyMatch(factory -> factory instanceof SecretBundleEnvironmentPostProcessor));
    }

    @Test
    void propertySourceDiagnosticsNeverPrintValues() {
        bundle("shared", shared());
        MockEnvironment environment = environment("dev");
        processor.postProcessEnvironment(environment, null);
        assertEquals(SecretBundleEnvironmentPostProcessor.SOURCE_NAME,
                environment.getPropertySources().get(SecretBundleEnvironmentPostProcessor.SOURCE_NAME).toString());
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment().withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "true")
                .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, profile)
                .withProperty("WARDEN_API_KEY", "fixture-original-warden-key");
        if (profile.equals("prod")) environment.setProperty("HYTALE_CLIENT_SECRET", "fixture-original-hytale-secret");
        return environment;
    }

    private void assertSafeFailure(MockEnvironment environment) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> processor.postProcessEnvironment(environment, null));
        assertEquals(SecretBundleEnvironmentPostProcessor.SAFE_ERROR, failure.getMessage());
        assertNull(failure.getCause());
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains("sensitive-fixture"));
        assertFalse(environment.getPropertySources().contains(SecretBundleEnvironmentPostProcessor.SOURCE_NAME));
    }

    private void bundle(String boundary, Map<String, String> values) {
        files.put(boundary + ".json", encoded(boundary, values));
    }

    private static byte[] encoded(String boundary, Map<String, String> values) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("boundary", boundary);
        envelope.put("secrets", values);
        return JSON.writeValueAsBytes(envelope);
    }

    private static Map<String, String> shared() {
        Map<String, String> values = entries(SecretBundleEnvironmentPostProcessor.SHARED);
        values.remove("MODTALE_PUBLIC_CACHE_PURGE_TOKEN");
        return values;
    }

    private static Map<String, String> preview(String boundary, String id) {
        Map<String, String> values = entries(boundary.equals("branch-preview")
                ? SecretBundleEnvironmentPostProcessor.BRANCH_BASE : SecretBundleEnvironmentPostProcessor.PR_BASE);
        values.putAll(dynamic(boundary, id));
        return values;
    }

    private static Map<String, String> dynamic(String boundary, String id) {
        String prefix = boundary + "-" + id + "-r2-";
        return entries(Set.of(prefix + "access-key", prefix + "secret-key", prefix + "endpoint", prefix + "token-id"));
    }

    private static Map<String, String> entries(Set<String> keys) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys) values.put(key, "fixture-" + key);
        return values;
    }

    @Configuration(proxyBeanMethods = false)
    static class ValueConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ValueBean valueBean() {
            return new ValueBean();
        }
    }

    static class ValueBean {
        @Value("${app.r2.secret-key}")
        String value;
    }
}
