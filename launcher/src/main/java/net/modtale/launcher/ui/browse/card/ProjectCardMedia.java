package net.modtale.launcher.ui.browse.card;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.value;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.ui.common.CachedImageLoader;

public final class ProjectCardMedia {

    private static final double CARD_RADIUS = 16;
    private static final double RESTING_OUTLINE_WIDTH = 1;
    private static final double HOVER_OUTLINE_WIDTH = 3;
    private static final double MAX_IMAGE_RENDER_SCALE = 3;
    private static final double PROJECT_ICON_RADIUS = 16;
    private static final double COMPACT_PROJECT_ICON_RADIUS = 10;

    private final CachedImageLoader imageLoader;
    private final double imageRenderScale;

    public ProjectCardMedia(Function<String, String> assetResolver, Executor executor) {
        this.imageLoader = new CachedImageLoader(assetResolver, executor);
        this.imageRenderScale = computeImageRenderScale();
    }

    public void clearImageCache() {
        imageLoader.clearMemory();
    }

    public StackPane banner(ProjectSummary project, double width, double height) {
        StackPane banner = new StackPane();
        banner.getStyleClass().add("project-banner");
        lockWidth(banner, width);
        banner.setMinHeight(height);
        banner.setPrefHeight(height);
        banner.setMaxHeight(height);
        banner.setClip(topRoundedClip(banner, 16));
        StackPane mediaLayer = new StackPane();
        mediaLayer.getStyleClass().add("project-banner-media");
        mediaLayer.setMouseTransparent(true);
        mediaLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(mediaLayer, Pos.TOP_CENTER);
        banner.getChildren().add(mediaLayer);

        if (project.bannerUrl() != null && !project.bannerUrl().isBlank()) {
            mediaLayer.getStyleClass().add("letterboxed");
            ImageView image = remoteBannerImage(project.bannerUrl(), width, height);
            image.fitWidthProperty().bind(banner.widthProperty());
            image.fitHeightProperty().bind(banner.heightProperty());
            image.setSmooth(true);
            image.setMouseTransparent(true);
            image.getStyleClass().add("project-banner-image");
            StackPane.setAlignment(image, Pos.CENTER);
            mediaLayer.getChildren().add(image);
        } else {
            Region fallback = new Region();
            fallback.getStyleClass().add("project-banner-fallback");
            fallback.setMouseTransparent(true);
            fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            mediaLayer.getChildren().add(fallback);
        }
        return banner;
    }

    public StackPane projectIcon(ProjectSummary project, double size, double borderWidth) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("project-icon");
        if (borderWidth <= 2) {
            icon.getStyleClass().add("project-icon-compact");
        }
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        icon.setPadding(new javafx.geometry.Insets(borderWidth));

        double mediaSize = Math.max(1, size - borderWidth * 2);
        StackPane media = new StackPane();
        media.getStyleClass().add("project-icon-media");
        media.setMinSize(mediaSize, mediaSize);
        media.setPrefSize(mediaSize, mediaSize);
        media.setMaxSize(mediaSize, mediaSize);

        Rectangle clip = new Rectangle(mediaSize, mediaSize);
        double mediaRadius = projectIconMediaRadius(borderWidth);
        clip.setArcWidth(mediaRadius * 2);
        clip.setArcHeight(mediaRadius * 2);
        media.setClip(clip);

        if (project.imageUrl() != null && !project.imageUrl().isBlank()) {
            media.getChildren().add(iconBackdrop(mediaSize));

            ImageView foreground = remoteImage(project.imageUrl(), mediaSize, mediaSize);
            foreground.setMouseTransparent(true);
            media.getChildren().add(foreground);
        } else {
            media.getStyleClass().add("project-icon-fallback-media");
            Label initial = new Label(value(project.title(), "M").substring(0, 1).toUpperCase(Locale.ROOT));
            media.getChildren().add(initial);
        }
        icon.getChildren().add(media);
        return icon;
    }

    public Rectangle hoverOutline(Region visualOwner, Region hoverOwner) {
        Rectangle outline = outline(visualOwner, HOVER_OUTLINE_WIDTH);
        outline.getStyleClass().add("project-hover-outline");
        outline.setStroke(Color.web("#3b82f6"));
        outline.visibleProperty().bind(hoverOwner.hoverProperty());
        return outline;
    }

    public Rectangle restingOutline(Region visualOwner) {
        Rectangle outline = outline(visualOwner, RESTING_OUTLINE_WIDTH);
        outline.setStroke(Color.rgb(255, 255, 255, 0.20));
        return outline;
    }

    public static void lockWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    public static void lockHeight(Region region, double height) {
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
    }

    private StackPane iconBackdrop(double size) {
        StackPane backdrop = new StackPane();
        backdrop.getStyleClass().add("project-icon-backdrop");
        backdrop.setMinSize(size, size);
        backdrop.setPrefSize(size, size);
        backdrop.setMaxSize(size, size);
        backdrop.setMouseTransparent(true);
        return backdrop;
    }

    private double projectIconMediaRadius(double borderWidth) {
        double outerRadius = borderWidth <= 2 ? COMPACT_PROJECT_ICON_RADIUS : PROJECT_ICON_RADIUS;
        return Math.max(0, outerRadius - borderWidth);
    }

    private Rectangle outline(Region visualOwner, double strokeWidth) {
        Rectangle outline = new Rectangle();
        double inset = strokeWidth / 2.0;
        outline.setX(inset);
        outline.setY(inset);
        outline.widthProperty().bind(visualOwner.widthProperty().subtract(strokeWidth));
        outline.heightProperty().bind(visualOwner.heightProperty().subtract(strokeWidth));
        outline.setArcWidth(CARD_RADIUS * 2);
        outline.setArcHeight(CARD_RADIUS * 2);
        outline.setFill(Color.TRANSPARENT);
        outline.setStrokeWidth(strokeWidth);
        outline.setMouseTransparent(true);
        outline.setManaged(false);
        return outline;
    }

    private Path topRoundedClip(Region owner, double radius) {
        MoveTo start = new MoveTo(0, radius);
        QuadCurveTo topLeft = new QuadCurveTo(0, 0, radius, 0);
        LineTo top = new LineTo();
        top.xProperty().bind(owner.widthProperty().subtract(radius));
        top.setY(0);
        QuadCurveTo topRight = new QuadCurveTo();
        topRight.controlXProperty().bind(owner.widthProperty());
        topRight.setControlY(0);
        topRight.xProperty().bind(owner.widthProperty());
        topRight.setY(radius);
        LineTo right = new LineTo();
        right.xProperty().bind(owner.widthProperty());
        right.yProperty().bind(owner.heightProperty());
        LineTo bottom = new LineTo();
        bottom.setX(0);
        bottom.yProperty().bind(owner.heightProperty());
        Path clip = new Path(start, topLeft, top, topRight, right, bottom, new ClosePath());
        clip.setFill(Color.BLACK);
        clip.setStroke(null);
        return clip;
    }

    private ImageView remoteImage(String rawUrl, double width, double height) {
        ImageView view = imageView(width, height, false);
        imageLoader.loadInto(view, rawUrl, requestedImageDimension(width, imageRenderScale), requestedImageDimension(height, imageRenderScale));
        return view;
    }

    private ImageView remoteBannerImage(String rawUrl, double width, double height) {
        ImageView view = imageView(width, height, true);
        imageLoader.loadInto(view, rawUrl,
                requestedImageDimension(width, imageRenderScale),
                requestedImageDimension(height, imageRenderScale),
                true);
        return view;
    }

    private ImageView imageView(double width, double height, boolean preserveRatio) {
        ImageView view = new ImageView();
        view.setPreserveRatio(preserveRatio);
        view.setFitWidth(width);
        if (height > 0) {
            view.setFitHeight(height);
        }
        view.setSmooth(true);
        return view;
    }

    private double computeImageRenderScale() {
        return Math.max(1, Math.min(MAX_IMAGE_RENDER_SCALE, Screen.getScreens().stream()
                .mapToDouble(screen -> Math.max(screen.getOutputScaleX(), screen.getOutputScaleY()))
                .max()
                .orElse(1)));
    }

    private double requestedImageDimension(double logicalSize, double renderScale) {
        if (!Double.isFinite(logicalSize) || logicalSize <= 0) {
            return 0;
        }
        return Math.ceil(logicalSize * renderScale);
    }
}
