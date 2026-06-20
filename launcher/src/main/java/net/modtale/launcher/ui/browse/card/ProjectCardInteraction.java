package net.modtale.launcher.ui.browse.card;

import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import net.modtale.launcher.model.project.ProjectSummary;

public final class ProjectCardInteraction {

    private static final String ANIMATION_CACHE_ENABLED_PROPERTY = "net.modtale.launcher.animationCacheEnabled";
    private static final String ANIMATION_CACHE_HINT_PROPERTY = "net.modtale.launcher.animationCacheHint";
    private static final Interpolator HOVER_EASE = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Duration HOVER_TRANSLATE_DURATION = Duration.millis(800);
    private static final Duration HOVER_SCALE_DURATION = Duration.millis(900);

    private ProjectCardInteraction() {
    }

    public static void openOnCardClick(Region card, ProjectSummary project, Consumer<ProjectSummary> onOpenPage) {
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (isInteractive(event.getTarget())) {
                return;
            }
            onOpenPage.accept(project);
        });
    }

    public static void addHoverAnimation(Region card, Node floatingIcon) {
        addHoverAnimation(card, floatingIcon, null);
    }

    public static void addHoverAnimation(Region card, Node floatingIcon, Node zoomMedia) {
        TranslateTransition cardTransition = hoverTransition(card);
        TranslateTransition iconTransition = hoverTransition(floatingIcon);
        ScaleTransition mediaTransition = zoomMedia == null ? null : scaleTransition(zoomMedia);
        card.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (hasHoverEffects(cardTransition, iconTransition, mediaTransition)) {
                resetHoverEffects(cardTransition, iconTransition, mediaTransition);
            }
        });
        card.setOnMouseEntered(event -> {
            if (isScrollActive(card)) {
                resetHoverEffects(cardTransition, iconTransition, mediaTransition);
                return;
            }
            animateY(cardTransition, -4);
            animateY(iconTransition, -7);
            animateScale(mediaTransition, 1.05);
        });
        card.setOnMouseExited(event -> {
            if (isScrollActive(card)) {
                resetHoverEffects(cardTransition, iconTransition, mediaTransition);
                return;
            }
            animateY(cardTransition, 0);
            animateY(iconTransition, 0);
            animateScale(mediaTransition, 1);
        });
    }

    private static boolean isInteractive(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node instanceof Button) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private static TranslateTransition hoverTransition(Node node) {
        TranslateTransition transition = new TranslateTransition(HOVER_TRANSLATE_DURATION, node);
        transition.setInterpolator(HOVER_EASE);
        transition.setOnFinished(event -> releaseAnimationCache(node));
        return transition;
    }

    private static ScaleTransition scaleTransition(Node node) {
        ScaleTransition transition = new ScaleTransition(HOVER_SCALE_DURATION, node);
        transition.setInterpolator(HOVER_EASE);
        transition.setOnFinished(event -> releaseAnimationCache(node));
        return transition;
    }

    private static void animateY(TranslateTransition transition, double y) {
        Node node = transition.getNode();
        if (Math.abs(node.getTranslateY() - y) < 0.1) {
            return;
        }
        transition.stop();
        transition.setFromY(node.getTranslateY());
        transition.setToY(y);
        prepareAnimationCache(node);
        transition.playFromStart();
    }

    private static void animateScale(ScaleTransition transition, double scale) {
        if (transition == null) {
            return;
        }
        Node node = transition.getNode();
        if (Math.abs(node.getScaleX() - scale) < 0.01 && Math.abs(node.getScaleY() - scale) < 0.01) {
            return;
        }
        transition.stop();
        transition.setFromX(node.getScaleX());
        transition.setFromY(node.getScaleY());
        transition.setToX(scale);
        transition.setToY(scale);
        prepareAnimationCache(node);
        transition.playFromStart();
    }

    private static void resetHoverOffsets(TranslateTransition... transitions) {
        for (TranslateTransition transition : transitions) {
            transition.stop();
            Node node = transition.getNode();
            if (Math.abs(node.getTranslateY()) >= 0.1) {
                node.setTranslateY(0);
            }
            releaseAnimationCache(node);
        }
    }

    private static void resetHoverEffects(
            TranslateTransition cardTransition,
            TranslateTransition iconTransition,
            ScaleTransition mediaTransition
    ) {
        resetHoverOffsets(cardTransition, iconTransition);
        if (mediaTransition != null) {
            mediaTransition.stop();
            Node node = mediaTransition.getNode();
            if (Math.abs(node.getScaleX() - 1) >= 0.01) {
                node.setScaleX(1);
            }
            if (Math.abs(node.getScaleY() - 1) >= 0.01) {
                node.setScaleY(1);
            }
            releaseAnimationCache(node);
        }
    }

    private static boolean hasHoverEffects(
            TranslateTransition cardTransition,
            TranslateTransition iconTransition,
            ScaleTransition mediaTransition
    ) {
        return isTranslateActive(cardTransition)
                || isTranslateActive(iconTransition)
                || isScaleActive(mediaTransition);
    }

    private static boolean isTranslateActive(TranslateTransition transition) {
        return transition.getStatus() == Animation.Status.RUNNING
                || Math.abs(transition.getNode().getTranslateY()) >= 0.1;
    }

    private static boolean isScaleActive(ScaleTransition transition) {
        if (transition == null) {
            return false;
        }
        Node node = transition.getNode();
        return transition.getStatus() == Animation.Status.RUNNING
                || Math.abs(node.getScaleX() - 1) >= 0.01
                || Math.abs(node.getScaleY() - 1) >= 0.01;
    }

    private static void prepareAnimationCache(Node node) {
        if (!node.getProperties().containsKey(ANIMATION_CACHE_ENABLED_PROPERTY)) {
            node.getProperties().put(ANIMATION_CACHE_ENABLED_PROPERTY, node.isCache());
            node.getProperties().put(ANIMATION_CACHE_HINT_PROPERTY, node.getCacheHint());
        }
        node.setCache(true);
        node.setCacheHint(CacheHint.SPEED);
    }

    private static void releaseAnimationCache(Node node) {
        Object cached = node.getProperties().remove(ANIMATION_CACHE_ENABLED_PROPERTY);
        Object hint = node.getProperties().remove(ANIMATION_CACHE_HINT_PROPERTY);
        if (!(cached instanceof Boolean wasCached)) {
            return;
        }
        node.setCache(wasCached);
        if (hint instanceof CacheHint cacheHint) {
            node.setCacheHint(cacheHint);
        } else {
            node.setCacheHint(CacheHint.DEFAULT);
        }
    }

    private static boolean isScrollActive(Node node) {
        return node.getScene() != null
                && Boolean.TRUE.equals(node.getScene().getRoot().getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY));
    }
}
