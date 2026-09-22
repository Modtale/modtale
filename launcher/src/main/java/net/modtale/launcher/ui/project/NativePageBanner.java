package net.modtale.launcher.ui.project;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherLayout;

final class NativePageBanner {
    private NativePageBanner() {}

    static StackPane create(String rootStyle, String stylePrefix, String url,
                            CachedImageLoader images, ReadOnlyDoubleProperty scrollPixels,
                            Runnable goBack, double fallbackHeight) {
        StackPane banner = new StackPane();
        banner.getStyleClass().add(rootStyle);
        banner.setMinWidth(0);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setPrefHeight(fallbackHeight);
        banner.setMaxHeight(Double.MAX_VALUE);
        StackPane media = new StackPane();
        media.getStyleClass().add(stylePrefix.equals("creator-profile")
                ? "creator-profile-banner" : stylePrefix + "-banner-media");
        media.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (url != null && !url.isBlank()) {
            media.getStyleClass().add("letterboxed");
            ImageView image = image(banner, stylePrefix + "-banner-image");
            images.loadInto(image, url, 1920, 640, true);
            media.getChildren().add(image);
        } else {
            Region fallback = new Region();
            fallback.getStyleClass().add(stylePrefix + "-banner-fallback");
            fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            media.getChildren().add(fallback);
        }
        Region fade = new Region();
        fade.getStyleClass().add(stylePrefix + "-banner-fade");
        fade.setMouseTransparent(true);
        NativeBannerScrollEffect.bind(media, fade, scrollPixels, banner.widthProperty());
        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.TOP_LEFT);
        backLayer.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(backLayer, Pos.TOP_CENTER);
        StackPane.setMargin(backLayer, LauncherLayout.launcherPageInsets(25, 0));
        Button back = new Button("Back", LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 16));
        back.getStyleClass().add(stylePrefix + "-back");
        back.setOnAction(event -> goBack.run());
        backLayer.getChildren().add(back);
        banner.getChildren().addAll(media, fade, backLayer);
        return banner;
    }

    static ImageView image(Region banner, String style) {
        ImageView image = new ImageView();
        image.getStyleClass().add(style);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        image.fitWidthProperty().bind(banner.widthProperty());
        image.fitHeightProperty().bind(banner.heightProperty());
        return image;
    }

    static double height(double width, double fallback) {
        return Double.isFinite(width) && width > 0 ? width / 3.0 : fallback;
    }
}
