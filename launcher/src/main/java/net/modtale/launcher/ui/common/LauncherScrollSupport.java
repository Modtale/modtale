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
    private final PauseTransition interactionCooldown = new PauseTransition(INTERACTION_IDLE_DELAY);

    public LauncherScrollSupport(Supplier<Node> rootSupplier) {
        this.rootSupplier = rootSupplier;
        interactionCooldown.setOnFinished(event -> clearScrollInteraction());
    }

    public void configure(ScrollPane scrollPane, boolean horizontal) {
        if (scrollPane == null || Boolean.TRUE.equals(scrollPane.getProperties().get(INSTALLED_PROPERTY))) {
            return;
        }
        scrollPane.getProperties().put(INSTALLED_PROPERTY, Boolean.TRUE);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::observeNativeScroll);
    }

    private void observeNativeScroll(ScrollEvent event) {
        long operationStart = LauncherPerformanceProbe.operationStartNanos();
        try {
            if (event.isControlDown()) {
                return;
            }
            activateScrollInteraction();
            interactionCooldown.playFromStart();
        } finally {
            LauncherPerformanceProbe.recordOperation("scroll.input", operationStart);
        }
    }

    private void activateScrollInteraction() {
        interactionCooldown.stop();
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
}
