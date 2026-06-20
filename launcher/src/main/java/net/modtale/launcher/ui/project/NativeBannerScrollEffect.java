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

    private NativeBannerScrollEffect() {
    }

    static void bind(Node media, Region fade, ReadOnlyDoubleProperty scrollPixels, double baseFadeHeight) {
        fade.setMaxWidth(Double.MAX_VALUE);
        fade.setMinHeight(baseFadeHeight);
        fade.setPrefHeight(baseFadeHeight);
        fade.setMaxHeight(baseFadeHeight);
        StackPane.setAlignment(fade, Pos.BOTTOM_CENTER);

        InvalidationListener listener = ignored -> apply(media, fade, scrollPixels.get(), baseFadeHeight);
        scrollPixels.addListener(listener);
        media.sceneProperty().addListener((observable, previous, current) -> {
            if (previous != null && current == null) {
                scrollPixels.removeListener(listener);
            }
        });
        apply(media, fade, scrollPixels.get(), baseFadeHeight);
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

    static double fadeScale(double offset, double baseFadeHeight) {
        if (!Double.isFinite(baseFadeHeight) || baseFadeHeight <= 0) {
            return 1;
        }
        return (baseFadeHeight + Math.max(0, offset)) / baseFadeHeight;
    }
}
