package net.modtale.launcher.ui.common;

import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import net.modtale.launcher.LauncherPerformanceProbe;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;

/**
 * Keeps expensive hover animations quiet while a scroll gesture is active.
 *
 * <p>Scrolling itself deliberately stays with JavaFX. Scroll events already contain
 * platform-scaled pixel deltas and, where supported, native inertia. Replacing those
 * values with another animation makes touchpads feel delayed and applies inertia a
 * second time.</p>
 */
public final class LauncherScrollSupport {

    private static final String INSTALLED_PROPERTY = LauncherScrollSupport.class.getName() + ".installed";
    private static final Duration INTERACTION_IDLE_DELAY = Duration.millis(140);

    private final Supplier<Node> rootSupplier;
    private final InteractionIdleTimer interactionIdleTimer;

    public LauncherScrollSupport(Supplier<Node> rootSupplier) {
        this.rootSupplier = rootSupplier;
        PauseTransition transition = new PauseTransition(INTERACTION_IDLE_DELAY);
        transition.setOnFinished(event -> clearScrollInteraction());
        this.interactionIdleTimer = new InteractionIdleTimer() {
            @Override
            public void restart() {
                transition.playFromStart();
            }

            @Override
            public void stop() {
                transition.stop();
            }
        };
    }

    LauncherScrollSupport(Supplier<Node> rootSupplier, InteractionIdleTimer interactionIdleTimer) {
        this.rootSupplier = rootSupplier;
        this.interactionIdleTimer = interactionIdleTimer;
    }

    public void configure(ScrollPane scrollPane, boolean horizontal) {
        configureNode(scrollPane);
    }

    void configureNode(Node scrollNode) {
        if (scrollNode == null || Boolean.TRUE.equals(scrollNode.getProperties().get(INSTALLED_PROPERTY))) {
            return;
        }
        scrollNode.getProperties().put(INSTALLED_PROPERTY, Boolean.TRUE);
        scrollNode.addEventFilter(ScrollEvent.SCROLL, this::observeNativeScroll);
    }

    private void observeNativeScroll(ScrollEvent event) {
        long operationStart = LauncherPerformanceProbe.operationStartNanos();
        try {
            if (event.isControlDown()) {
                return;
            }
            activateScrollInteraction();
            interactionIdleTimer.restart();
        } finally {
            LauncherPerformanceProbe.recordOperation("scroll.input", operationStart);
        }
    }

    private void activateScrollInteraction() {
        interactionIdleTimer.stop();
        Node root = rootSupplier.get();
        if (root != null) {
            root.getProperties().put(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY, Boolean.TRUE);
        }
    }

    private void clearScrollInteraction() {
        Node root = rootSupplier.get();
        if (root != null) {
            root.getProperties().remove(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY);
        }
    }

    interface InteractionIdleTimer {
        void restart();

        void stop();
    }
}
