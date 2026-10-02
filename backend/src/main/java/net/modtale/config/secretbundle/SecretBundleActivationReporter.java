package net.modtale.config.secretbundle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Secret-free readiness evidence. Missing evidence never proves legacy mode. */
@Component
public final class SecretBundleActivationReporter implements ApplicationListener<ApplicationReadyEvent> {
    private final ConfigurableApplicationContext context;
    private final Consumer<String> emitter;
    private final AtomicBoolean reported = new AtomicBoolean();

    @Autowired
    public SecretBundleActivationReporter(ConfigurableApplicationContext context) {
        this(context, System.out::println);
    }

    SecretBundleActivationReporter(ConfigurableApplicationContext context, Consumer<String> emitter) {
        this.context = context;
        this.emitter = emitter;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() != context) {
            return;
        }
        String profile = SecretBundleEnvironmentPostProcessor.activatedProfile(context.getEnvironment());
        if (profile == null || !reported.compareAndSet(false, true)) {
            return;
        }
        try {
            // Profile comes from private immutable loader metadata, never an env override.
            // One JSON line lets Cloud Logging correlate evidence to its revision resource.
            emitter.accept("{\"logging.googleapis.com/labels\":{\"modtale_secret_bundle_activation\":\"v1\"},\"severity\":\"INFO\",\"event\":\"modtale_secret_bundle_activation\",\"activated\":true,\"profile\":\""
                    + profile + "\"}");
        } catch (RuntimeException ignored) {
            // Observability must not fail a healthy startup or reveal emitter diagnostics.
        }
    }
}
