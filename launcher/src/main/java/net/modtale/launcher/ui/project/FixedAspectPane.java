package net.modtale.launcher.ui.project;

import javafx.scene.layout.StackPane;

final class FixedAspectPane extends StackPane {

    private static final double FALLBACK_WIDTH = 640;

    private final double aspectRatio;

    FixedAspectPane(double aspectRatio) {
        if (!Double.isFinite(aspectRatio) || aspectRatio <= 0) {
            throw new IllegalArgumentException("aspectRatio must be positive");
        }
        this.aspectRatio = aspectRatio;
        setMinWidth(0);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width) {
        return effectiveWidth(width) / aspectRatio;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        if (Double.isFinite(height) && height > 0) {
            return height * aspectRatio;
        }
        double prefWidth = getPrefWidth();
        if (Double.isFinite(prefWidth) && prefWidth > 0) {
            return prefWidth;
        }
        return FALLBACK_WIDTH;
    }

    private double effectiveWidth(double width) {
        if (Double.isFinite(width) && width > 0) {
            return width;
        }
        double prefWidth = getPrefWidth();
        if (Double.isFinite(prefWidth) && prefWidth > 0) {
            return prefWidth;
        }
        return FALLBACK_WIDTH;
    }
}
