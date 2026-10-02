package net.modtale.config.secretbundle;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SecretBundleActivationReporterTest {
    private static final String PRIVATE_VALUE = "SYNTHETIC-PRIVATE-VALUE";
    private static final String MALICIOUS_PROFILE = "dev\"}\n{\"SYNTHETIC-PRIVATE\":true}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TestFactory
    Stream<DynamicTest> realLoaderProfilesEmitOnlyFixedSecretFreeEvidenceAfterReady() {
        return Stream.of("prod", "dev", "branch-preview", "pr-preview").map(profile -> DynamicTest.dynamicTest(profile, () -> {
            MockEnvironment environment = activated(profile);
            List<String> events = new ArrayList<>();
            try (var context = context(environment, events)) {
                // Context refresh and an arbitrary unrelated event are not readiness.
                assertTrue(events.isEmpty());
                context.publishEvent("SYNTHETIC-PRIVATE-UNRELATED-EVENT");
                assertTrue(events.isEmpty());
                context.publishEvent(ready(context));
                assertEquals(List.of("{\"severity\":\"INFO\",\"event\":\"modtale_secret_bundle_activation\",\"activated\":true,\"profile\":\""
                        + profile + "\"}"), events);
                var evidence = JSON.readTree(events.getFirst());
                assertEquals(Set.of("severity", "event", "activated", "profile"), Set.copyOf(evidence.propertyNames()));
                assertTrue(evidence.get("activated").asBoolean());
                assertEquals(profile, evidence.get("profile").stringValue());
                assertFalse(events.getFirst().contains("SYNTHETIC-PRIVATE"));
                assertFalse(events.getFirst().contains("preview-sentinel"));
                assertFalse(events.getFirst().contains("98327462"));
                assertFalse(events.getFirst().contains("R2_SECRET_KEY"));
                assertFalse(events.getFirst().contains("WARDEN_API_KEY"));
                assertFalse(events.getFirst().contains("HYTALE_CLIENT_SECRET"));
            }
        }));
    }

    @Test
    void missingSourceAndLegacyModeEmitNothingEvenWithActivationClaims() {
        for (String flag : new String[]{"false", "true"}) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, flag)
                    .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, "prod")
                    .withProperty("activated", "true");
            List<String> events = new ArrayList<>();
            try (var context = context(environment, events)) {
                context.publishEvent(ready(context));
                assertTrue(events.isEmpty());
            }
        }
    }

    @Test
    void spoofedNamedPropertySourceCannotForgeActivationOrLogItsPayload() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(SecretBundleEnvironmentPostProcessor.SOURCE_NAME,
                Map.of("activated", true, "profile", MALICIOUS_PROFILE,
                        SecretBundleEnvironmentPostProcessor.PROFILE, MALICIOUS_PROFILE)) {
            @Override
            public Object getProperty(String name) {
                fail("The reporter must not inspect values in an untrusted source.");
                return null;
            }
        });
        List<String> events = new ArrayList<>();
        // Construct directly to isolate the reporter from Spring's unrelated property reads.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            var reporter = new SecretBundleActivationReporter(context, events::add);
            reporter.onApplicationEvent(ready(context));
            assertTrue(events.isEmpty());
            assertNull(SecretBundleEnvironmentPostProcessor.activatedProfile(environment));
        }
    }

    @Test
    void laterEnvironmentOverridesCannotChangeLoaderRecordedProfile() throws IOException {
        MockEnvironment environment = activated("dev");
        environment.getPropertySources().addFirst(new MapPropertySource("maliciousOverrides", Map.of(
                SecretBundleEnvironmentPostProcessor.PROFILE, MALICIOUS_PROFILE,
                SecretBundleEnvironmentPostProcessor.ENABLED, "false", "activated", false)));
        List<String> events = new ArrayList<>();
        try (var context = context(environment, events)) {
            context.publishEvent(ready(context));
            assertEquals("dev", JSON.readTree(events.getFirst()).get("profile").stringValue());
            assertFalse(events.getFirst().contains("SYNTHETIC-PRIVATE"));
        }
    }

    @Test
    void invalidLoaderNeverPublishesActivationEvidence() {
        MockEnvironment environment = enabled("dev");
        var processor = new SecretBundleEnvironmentPostProcessor(path -> {
            throw new IOException(PRIVATE_VALUE);
        });
        assertThrows(IllegalStateException.class, () -> processor.postProcessEnvironment(environment, null));
        List<String> events = new ArrayList<>();
        try (var context = context(environment, events)) {
            context.publishEvent(ready(context));
            assertTrue(events.isEmpty());
        }
    }

    @Test
    void repeatedReadyEventsEmitAtMostOnce() throws IOException {
        List<String> events = new ArrayList<>();
        try (var context = context(activated("dev"), events)) {
            for (int count = 0; count < 5; count++) context.publishEvent(ready(context));
            assertEquals(1, events.size());
        }
    }

    @Test
    void emitterFailureCannotFailStartupOrExposeItsCause() throws IOException {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(activated("dev"));
            AtomicInteger attempts = new AtomicInteger();
            var reporter = new SecretBundleActivationReporter(context, ignored -> {
                attempts.incrementAndGet();
                throw new IllegalStateException(PRIVATE_VALUE, new IOException(PRIVATE_VALUE));
            });
            assertDoesNotThrow(() -> reporter.onApplicationEvent(ready(context)));
            assertDoesNotThrow(() -> reporter.onApplicationEvent(ready(context)));
            assertEquals(1, attempts.get());
        }
    }

    @Test
    void otherContextReadyEventCannotClaimThisApplicationIsReady() throws IOException {
        List<String> events = new ArrayList<>();
        try (var context = context(activated("dev"), events); var other = new AnnotationConfigApplicationContext()) {
            other.setEnvironment(activated("prod"));
            context.publishEvent(ready(other));
            assertTrue(events.isEmpty());
            context.publishEvent(ready(context));
            assertEquals(1, events.size());
        }
    }

    @Test
    void reporterIsConstructibleBySpringWithoutExtraConfiguration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.register(SecretBundleActivationReporter.class);
            context.refresh();
            assertNotNull(context.getBean(SecretBundleActivationReporter.class));
            context.publishEvent(ready(context));
        }
    }

    private static AnnotationConfigApplicationContext context(MockEnvironment environment, List<String> events) {
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.registerBean(SecretBundleActivationReporter.class, () -> new SecretBundleActivationReporter(context, events::add));
        context.refresh();
        return context;
    }

    private static ApplicationReadyEvent ready(AnnotationConfigApplicationContext context) {
        return new ApplicationReadyEvent(new SpringApplication(SecretBundleActivationReporterTest.class),
                new String[0], context, Duration.ZERO);
    }

    private static MockEnvironment enabled(String profile) {
        return new MockEnvironment().withProperty(SecretBundleEnvironmentPostProcessor.ENABLED, "true")
                .withProperty(SecretBundleEnvironmentPostProcessor.PROFILE, profile)
                .withProperty("WARDEN_API_KEY", PRIVATE_VALUE).withProperty("HYTALE_CLIENT_SECRET", PRIVATE_VALUE);
    }

    private static MockEnvironment activated(String profile) throws IOException {
        MockEnvironment environment = enabled(profile);
        String boundary = profile.equals("prod") || profile.equals("dev") ? "shared" : profile;
        Set<String> keys = switch (boundary) {
            case "shared" -> SecretBundleEnvironmentPostProcessor.SHARED;
            case "branch-preview" -> SecretBundleEnvironmentPostProcessor.BRANCH_BASE;
            default -> SecretBundleEnvironmentPostProcessor.PR_BASE;
        };
        Map<String, String> values = new LinkedHashMap<>();
        keys.forEach(key -> values.put(key, PRIVATE_VALUE));
        if (!boundary.equals("shared")) {
            String id = boundary.equals("branch-preview") ? "preview-sentinel" : "98327462";
            environment.setProperty(SecretBundleEnvironmentPostProcessor.PREVIEW_ID, id);
            for (String suffix : new String[]{"access-key", "secret-key", "endpoint", "token-id"}) {
                values.put(boundary + "-" + id + "-r2-" + suffix, PRIVATE_VALUE);
            }
        }
        byte[] payload = JSON.writeValueAsBytes(Map.of("schemaVersion", 1, "boundary", boundary, "secrets", values));
        new SecretBundleEnvironmentPostProcessor(path -> payload).postProcessEnvironment(environment, null);
        return environment;
    }
}
