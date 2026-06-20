package net.modtale.launcher.ui.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeGalleryCarousel {

    private static final double MEDIA_ASPECT_RATIO = 16.0 / 9.0;
    private static final double MODAL_REQUESTED_WIDTH = 1800;
    private static final double MODAL_REQUESTED_HEIGHT = 1100;
    private static final double INLINE_REQUESTED_WIDTH = 1200;
    private static final double INLINE_REQUESTED_HEIGHT = 720;
    private static final double THUMBNAIL_WIDTH = 144;
    private static final double THUMBNAIL_HEIGHT = 80;
    private static final double THUMBNAIL_REQUESTED_WIDTH = 256;
    private static final double THUMBNAIL_REQUESTED_HEIGHT = 144;
    private static final Duration AUTO_ADVANCE_DURATION = Duration.seconds(8);
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final String THUMBNAIL_LOADED_PROPERTY = NativeGalleryCarousel.class.getName() + ".thumbnailLoaded";

    enum Variant {
        MODAL,
        INLINE
    }

    private final CachedImageLoader imageLoader;
    private final Consumer<String> openUrl;

    NativeGalleryCarousel(CachedImageLoader imageLoader, Consumer<String> openUrl) {
        this.imageLoader = imageLoader;
        this.openUrl = openUrl;
    }

    Node render(List<ImageItem> images, int initialIndex, Variant variant) {
        return new CarouselView(images, initialIndex, variant).root();
    }

    private final class CarouselView {
        private final List<ImageItem> images;
        private final Variant variant;
        private final VBox root = new VBox(0);
        private final StackPane media = new FixedAspectPane(MEDIA_ASPECT_RATIO);
        private final ImageView image = new ImageView();
        private final Button previous = arrow(LauncherIcons.Glyph.CHEVRON_LEFT, "Previous image");
        private final Button next = arrow(LauncherIcons.Glyph.CHEVRON_RIGHT, "Next image");
        private final Label caption = new Label();
        private final HBox thumbnails = new HBox(12);
        private final ScrollPane thumbnailScroller = new ScrollPane(thumbnails);
        private final Region progressFill = new Region();
        private final Scale progressScale = new Scale(0, 1, 0, 0);
        private final List<ImageView> thumbnailViews = new ArrayList<>();
        private final List<Button> thumbnailButtons;
        private Timeline progressTimeline;
        private int index;

        private CarouselView(List<ImageItem> images, int initialIndex, Variant variant) {
            this.images = images == null ? List.of() : List.copyOf(images);
            this.variant = variant;
            this.index = normalizedIndex(initialIndex);
            this.thumbnailButtons = buildThumbnailButtons();
            build();
            update();
        }

        private Node root() {
            return root;
        }

        private void build() {
            root.getStyleClass().addAll("project-gallery-carousel", variant == Variant.MODAL ? "modal" : "inline");
            root.setFocusTraversable(true);
            root.setMinWidth(0);
            root.setMaxWidth(Double.MAX_VALUE);

            media.getStyleClass().add("project-gallery-carousel-media");
            media.setFocusTraversable(false);
            media.setMinWidth(0);
            media.setMaxWidth(Double.MAX_VALUE);
            media.prefWidthProperty().bind(root.widthProperty());
            media.maxWidthProperty().bind(root.widthProperty());
            media.prefHeightProperty().bind(root.widthProperty().multiply(1.0 / MEDIA_ASPECT_RATIO));
            media.maxHeightProperty().bind(media.prefHeightProperty());

            image.getStyleClass().add("project-gallery-carousel-image");
            image.setPreserveRatio(true);
            image.setSmooth(true);
            image.fitWidthProperty().bind(media.widthProperty());
            image.fitHeightProperty().bind(media.heightProperty());
            media.getChildren().add(image);

            previous.getStyleClass().add("previous");
            previous.setOnAction(event -> show(index - 1));
            next.getStyleClass().add("next");
            next.setOnAction(event -> show(index + 1));
            StackPane.setAlignment(previous, Pos.CENTER_LEFT);
            StackPane.setMargin(previous, new Insets(0, 0, 0, 16));
            StackPane.setAlignment(next, Pos.CENTER_RIGHT);
            StackPane.setMargin(next, new Insets(0, 16, 0, 0));

            StackPane progressTrack = progressTrack();
            StackPane.setAlignment(progressTrack, Pos.BOTTOM_CENTER);

            caption.getStyleClass().add("project-gallery-carousel-caption");
            caption.setWrapText(true);
            caption.setMaxWidth(Double.MAX_VALUE);

            if (images.size() > 1) {
                media.getChildren().addAll(previous, next, progressTrack);
            }
            root.getChildren().add(media);
            root.getChildren().add(caption);
            if (images.size() > 1) {
                configureThumbnailScroller();
                root.getChildren().add(thumbnailScroller);
            }

            root.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.LEFT) {
                    show(index - 1);
                    event.consume();
                } else if (event.getCode() == KeyCode.RIGHT) {
                    show(index + 1);
                    event.consume();
                }
            });
            root.sceneProperty().addListener((observable, previousScene, currentScene) -> {
                if (currentScene == null) {
                    stopProgressTimeline();
                } else {
                    restartProgressTimeline();
                }
            });
        }

        private Button arrow(LauncherIcons.Glyph glyph, String accessibleText) {
            Button button = new Button(null, LauncherIcons.icon(glyph, 26));
            button.getStyleClass().add("project-gallery-carousel-arrow");
            button.setAccessibleText(accessibleText);
            return button;
        }

        private StackPane progressTrack() {
            Pane fillOwner = new Pane(progressFill);
            fillOwner.getStyleClass().add("project-gallery-carousel-progress-track");
            fillOwner.setMinHeight(4);
            fillOwner.setPrefHeight(4);
            fillOwner.setMaxHeight(4);
            progressFill.getStyleClass().add("project-gallery-carousel-progress-fill");
            progressFill.prefHeightProperty().bind(fillOwner.heightProperty());
            progressFill.minHeightProperty().bind(fillOwner.heightProperty());
            progressFill.maxHeightProperty().bind(fillOwner.heightProperty());
            progressFill.prefWidthProperty().bind(fillOwner.widthProperty());
            progressFill.minWidthProperty().bind(fillOwner.widthProperty());
            progressFill.maxWidthProperty().bind(fillOwner.widthProperty());
            progressFill.getTransforms().setAll(progressScale);
            StackPane track = new StackPane(fillOwner);
            track.setMouseTransparent(true);
            track.setMaxWidth(Double.MAX_VALUE);
            track.setMinHeight(4);
            track.setPrefHeight(4);
            track.setMaxHeight(4);
            return track;
        }

        private void configureThumbnailScroller() {
            thumbnailScroller.getStyleClass().add("project-gallery-carousel-thumbnails");
            thumbnailScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            thumbnailScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            thumbnailScroller.setFitToHeight(true);
            thumbnailScroller.setPannable(true);
            thumbnailScroller.setMinHeight(108);
            thumbnailScroller.setPrefHeight(108);
            thumbnailScroller.setMaxHeight(108);
            thumbnailScroller.setMaxWidth(Double.MAX_VALUE);
            thumbnails.getStyleClass().add("project-gallery-carousel-thumbnail-row");
            thumbnails.setAlignment(Pos.CENTER_LEFT);
            thumbnails.getChildren().setAll(thumbnailButtons);
        }

        private List<Button> buildThumbnailButtons() {
            List<Button> buttons = new ArrayList<>(images.size());
            for (int i = 0; i < images.size(); i++) {
                buttons.add(thumbnailButton(images.get(i), i));
            }
            return List.copyOf(buttons);
        }

        private Button thumbnailButton(ImageItem item, int itemIndex) {
            Button button = new Button();
            button.getStyleClass().add("project-gallery-carousel-thumbnail");
            button.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            button.setMinSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            button.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            button.setMaxSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            button.setGraphic(thumbnailGraphic(item, itemIndex));
            button.setAccessibleText(item.alt().isBlank() ? "Gallery thumbnail" : item.alt());
            button.setOnAction(event -> show(thumbnailButtons.indexOf(button)));
            return button;
        }

        private Node thumbnailGraphic(ImageItem item, int itemIndex) {
            StackPane frame = new StackPane();
            frame.getStyleClass().add("project-gallery-carousel-thumbnail-frame");
            frame.setMinSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            frame.setPrefSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            frame.setMaxSize(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            Rectangle clip = new Rectangle(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
            clip.setArcWidth(6);
            clip.setArcHeight(6);
            frame.setClip(clip);
            ImageView thumbnail = new ImageView();
            thumbnail.getStyleClass().add("project-gallery-carousel-thumbnail-image");
            thumbnail.setSmooth(true);
            thumbnail.setPreserveRatio(true);
            thumbnail.setFitWidth(THUMBNAIL_WIDTH);
            thumbnail.setFitHeight(THUMBNAIL_HEIGHT);
            thumbnailViews.add(thumbnail);
            if (shouldLoadThumbnail(itemIndex)) {
                loadThumbnail(itemIndex);
            }
            frame.getChildren().add(thumbnail);
            if (item.youtube()) {
                frame.getChildren().add(playBadge(28));
            }
            return frame;
        }

        private Node playBadge(double size) {
            StackPane badge = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.CIRCLE, size));
            badge.getStyleClass().add("project-gallery-carousel-play");
            badge.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_RIGHT, size * 0.46));
            badge.setMouseTransparent(true);
            return badge;
        }

        private void show(int nextIndex) {
            if (images.isEmpty()) {
                return;
            }
            index = (nextIndex % images.size() + images.size()) % images.size();
            update();
        }

        private void update() {
            if (images.isEmpty()) {
                image.setImage(null);
                caption.setText("");
                setCaptionVisible(false);
                stopProgressTimeline();
                return;
            }
            ImageItem item = images.get(index);
            image.setAccessibleText(item.alt());
            imageLoader.loadInto(image, item.previewUrl(), requestedWidth(), requestedHeight(), true);
            loadNearbyThumbnails();
            String captionText = item.caption();
            caption.setText(captionText);
            setCaptionVisible(!captionText.isBlank());
            for (int i = 0; i < thumbnailButtons.size(); i++) {
                thumbnailButtons.get(i).pseudoClassStateChanged(SELECTED, i == index);
            }
            if (item.hasOpenTarget()) {
                image.setCursor(Cursor.HAND);
                image.setOnMouseClicked(event -> openUrl.accept(item.openTarget()));
            } else {
                image.setCursor(Cursor.DEFAULT);
                image.setOnMouseClicked(null);
            }
            restartProgressTimeline();
        }

        private void setCaptionVisible(boolean visible) {
            caption.setVisible(visible);
            caption.setManaged(visible);
        }

        private void loadNearbyThumbnails() {
            for (int i = 0; i < thumbnailViews.size(); i++) {
                if (shouldLoadThumbnail(i)) {
                    loadThumbnail(i);
                }
            }
        }

        private boolean shouldLoadThumbnail(int itemIndex) {
            return itemIndex < 5 || Math.abs(itemIndex - index) <= 2;
        }

        private void loadThumbnail(int itemIndex) {
            if (itemIndex < 0 || itemIndex >= thumbnailViews.size() || itemIndex >= images.size()) {
                return;
            }
            ImageView thumbnail = thumbnailViews.get(itemIndex);
            if (Boolean.TRUE.equals(thumbnail.getProperties().get(THUMBNAIL_LOADED_PROPERTY))) {
                return;
            }
            thumbnail.getProperties().put(THUMBNAIL_LOADED_PROPERTY, true);
            imageLoader.loadInto(thumbnail, images.get(itemIndex).previewUrl(), THUMBNAIL_REQUESTED_WIDTH, THUMBNAIL_REQUESTED_HEIGHT, true);
        }

        private void restartProgressTimeline() {
            stopProgressTimeline();
            if (images.size() <= 1 || root.getScene() == null) {
                progressScale.setX(0);
                return;
            }
            progressScale.setX(0);
            progressTimeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progressScale.xProperty(), 0)),
                    new KeyFrame(AUTO_ADVANCE_DURATION, event -> {
                        show(index + 1);
                    }, new KeyValue(progressScale.xProperty(), 1, Interpolator.LINEAR))
            );
            progressTimeline.play();
        }

        private void stopProgressTimeline() {
            if (progressTimeline != null) {
                progressTimeline.stop();
                progressTimeline = null;
            }
        }

        private double requestedWidth() {
            return variant == Variant.MODAL ? MODAL_REQUESTED_WIDTH : INLINE_REQUESTED_WIDTH;
        }

        private double requestedHeight() {
            return variant == Variant.MODAL ? MODAL_REQUESTED_HEIGHT : INLINE_REQUESTED_HEIGHT;
        }

        private int normalizedIndex(int requestedIndex) {
            if (images == null || images.isEmpty()) {
                return 0;
            }
            return Math.max(0, Math.min(requestedIndex, images.size() - 1));
        }
    }

    record ImageItem(String url, String alt, String linkUrl, String caption) {
        ImageItem {
            url = url == null ? "" : url.trim();
            alt = alt == null ? "" : alt.trim();
            linkUrl = linkUrl == null ? "" : linkUrl.trim();
            caption = caption == null ? "" : caption.trim();
        }

        ImageItem(String url, String alt, String linkUrl) {
            this(url, alt, linkUrl, "");
        }

        boolean hasLink() {
            return !linkUrl.isBlank();
        }

        boolean hasOpenTarget() {
            return hasLink() || youtube();
        }

        String openTarget() {
            return hasLink() ? linkUrl : url;
        }

        boolean youtube() {
            return youtubeVideoId(url) != null;
        }

        String previewUrl() {
            String videoId = youtubeVideoId(url);
            return videoId == null ? url : "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
        }

        private static String youtubeVideoId(String rawUrl) {
            String value = rawUrl == null ? "" : rawUrl.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            for (String marker : List.of("youtu.be/", "youtube.com/shorts/", "youtube.com/embed/")) {
                int index = lower.indexOf(marker);
                if (index >= 0) {
                    return cleanVideoId(value.substring(index + marker.length()));
                }
            }
            int watchIndex = lower.indexOf("v=");
            if (lower.contains("youtube.com/watch") && watchIndex >= 0) {
                return cleanVideoId(value.substring(watchIndex + 2));
            }
            return null;
        }

        private static String cleanVideoId(String value) {
            int end = value.length();
            for (String delimiter : List.of("&", "?", "#", "/", "\"", "'")) {
                int index = value.indexOf(delimiter);
                if (index >= 0) {
                    end = Math.min(end, index);
                }
            }
            String id = value.substring(0, end).trim();
            return id.isBlank() ? null : id;
        }
    }
}
