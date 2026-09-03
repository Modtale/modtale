package net.modtale.launcher.ui.project;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.LauncherPerformanceProbe;

final class NativeBannerScrollEffect {

    private static final double SCROLL_CAP = 1500;
    private static final double PARALLAX_MAX_OFFSET = 500;
    private static final double PARALLAX_DECAY = 600;
    private static final double COMPACT_BREAKPOINT = 768;
    private static final double COMPACT_FADE_HEIGHT = 8;
    private static final double DESKTOP_FADE_HEIGHT = 128;

    private NativeBannerScrollEffect() {
    }

    static void bind(
            Node media,
            Region fade,
            ReadOnlyDoubleProperty scrollPixels,
            ReadOnlyDoubleProperty viewportWidth
    ) {
        InvalidationListener scrollListener = ignored -> apply(
                media, fade, scrollPixels.get(), baseFadeHeight(viewportWidth.get()));
        InvalidationListener widthListener = ignored -> {
            double baseFadeHeight = baseFadeHeight(viewportWidth.get());
            configureFade(fade, baseFadeHeight);
            apply(media, fade, scrollPixels.get(), baseFadeHeight);
        };
        scrollPixels.addListener(scrollListener);
        viewportWidth.addListener(widthListener);
        media.sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null && current == null) {
                scrollPixels.removeListener(scrollListener);
                viewportWidth.removeListener(widthListener);
            }
        });
        widthListener.invalidated(viewportWidth);
    }

    private static void configureFade(Region fade, double baseFadeHeight) {
        fade.setMaxWidth(Double.MAX_VALUE);
        fade.setMinHeight(baseFadeHeight);
        fade.setPrefHeight(baseFadeHeight);
        fade.setMaxHeight(baseFadeHeight);
        StackPane.setAlignment(fade, Pos.BOTTOM_CENTER);
    }

    private static void apply(Node media, Region fade, double scrollPixels, double baseFadeHeight) {
        long operationStart = LauncherPerformanceProbe.operationStartNanos();
        try {
            double offset = parallaxOffset(scrollPixels);
            media.setTranslateY(offset);
            fade.setTranslateY(offset / 2.0);
            fade.setScaleY(fadeScale(offset, baseFadeHeight));
        } finally {
            LauncherPerformanceProbe.recordOperation("banner.scrollEffect", operationStart);
        }
    }

    static double parallaxOffset(double scrollPixels) {
        double scroll = Math.min(Math.max(0, scrollPixels), SCROLL_CAP);
        return PARALLAX_MAX_OFFSET * (1 - Math.exp(-scroll / PARALLAX_DECAY));
    }

    static double baseFadeHeight(double viewportWidth) {
        return Double.isFinite(viewportWidth) && viewportWidth >= COMPACT_BREAKPOINT
                ? DESKTOP_FADE_HEIGHT
                : COMPACT_FADE_HEIGHT;
    }

    static double fadeScale(double offset, double baseFadeHeight) {
        if (!Double.isFinite(baseFadeHeight) || baseFadeHeight <= 0) {
            return 1;
        }
        return (baseFadeHeight + Math.max(0, offset)) / baseFadeHeight;
    }
}
