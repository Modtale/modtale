package net.modtale.config.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppR2PropertiesBindingTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "app.r2.bucket=media-fixture",
                    "app.r2.access-key=synthetic-access",
                    "app.r2.secret-key=synthetic-secret",
                    "app.r2.endpoint=https://storage.example.test");

    @Test
    void springContextBindsAllSixPropertiesWithBothConstructorsPresent() {
        context.withPropertyValues(
                "app.r2.public-domain=https://cdn.example.test",
                "app.r2.artifact-bucket=private-fixture"
        ).run(application -> {
            assertNull(application.getStartupFailure());
            AppR2Properties properties = application.getBean(AppR2Properties.class);
            assertAll(
                    () -> assertEquals("media-fixture", properties.bucket()),
                    () -> assertEquals("synthetic-access", properties.accessKey()),
                    () -> assertEquals("synthetic-secret", properties.secretKey()),
                    () -> assertEquals("https://storage.example.test", properties.endpoint()),
                    () -> assertEquals("https://cdn.example.test", properties.publicDomain()),
                    () -> assertEquals("private-fixture", properties.artifactBucket()));
        });
    }

    @Test
    void isolatedPreviewContextBindsEmptyPublicDomainAndArtifactBucket() {
        context.withPropertyValues("app.r2.public-domain=", "app.r2.artifact-bucket=")
                .run(application -> {
                    assertNull(application.getStartupFailure());
                    AppR2Properties properties = application.getBean(AppR2Properties.class);
                    assertEquals("media-fixture", properties.bucket());
                    assertEquals("", properties.publicDomain());
                    assertEquals("", properties.artifactBucket());
                });
    }

    @Test
    void compatibilityConstructorPreservesFiveFieldsAndLeavesArtifactBucketUnset() {
        AppR2Properties properties = new AppR2Properties(
                "media-fixture", "synthetic-access", "synthetic-secret",
                "https://storage.example.test", "https://cdn.example.test");
        assertAll(
                () -> assertEquals("media-fixture", properties.bucket()),
                () -> assertEquals("synthetic-access", properties.accessKey()),
                () -> assertEquals("synthetic-secret", properties.secretKey()),
                () -> assertEquals("https://storage.example.test", properties.endpoint()),
                () -> assertEquals("https://cdn.example.test", properties.publicDomain()),
                () -> assertNull(properties.artifactBucket()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppR2Properties.class)
    static class PropertiesConfiguration {}
}
