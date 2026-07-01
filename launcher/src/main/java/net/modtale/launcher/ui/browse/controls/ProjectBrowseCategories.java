package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherScrollSupport;

public final class ProjectBrowseCategories {

    private static final double VIEWPORT_CHROME_BUFFER = 8;
    private static final double OVERFLOW_TOLERANCE = 12;
    private static final Interpolator PILL_EASE = Interpolator.SPLINE(0.16, 1.0, 0.30, 1.0);
    private static final Duration PILL_TRANSITION_DURATION = Duration.millis(700);

    private final LauncherScrollSupport scrollSupport;
    private final Runnable onSearch;
    private final Map<BrowseOptions.ClassificationOption, Button> categoryButtons = new LinkedHashMap<>();
    private final Region pillIndicator = new Region();
    private final Region leftFade = new Region();
    private final Region rightFade = new Region();
    private final ReadOnlyDoubleWrapper contentWidth = new ReadOnlyDoubleWrapper();
    private final ReadOnlyBooleanWrapper overflowing = new ReadOnlyBooleanWrapper();

    private Timeline pillTimeline;
    private BrowseOptions.ClassificationOption selectedClassification = BrowseOptions.ClassificationOption.defaultOption();
    private Node view;

    public ProjectBrowseCategories(LauncherScrollSupport scrollSupport, Runnable onSearch) {
        this.scrollSupport = scrollSupport;
        this.onSearch = onSearch;
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public BrowseOptions.ClassificationOption selectedClassification() {
        return selectedClassification;
    }

    public double contentWidth() {
        return contentWidth.get();
    }

    public ReadOnlyDoubleProperty contentWidthProperty() {
        return contentWidth.getReadOnlyProperty();
    }

    public boolean isOverflowing() {
        return overflowing.get();
    }

    public ReadOnlyBooleanProperty overflowingProperty() {
        return overflowing.getReadOnlyProperty();
    }

    public void selectClassification(BrowseOptions.ClassificationOption classification) {
        selectedClassification = classification == null
                ? BrowseOptions.ClassificationOption.defaultOption()
                : classification;
        refresh();
        onSearch.run();
    }

    public void refresh() {
        categoryButtons.forEach((key, button) -> pseudo(button, "selected", key == selectedClassification));
        animatePill();
    }

    private Node buildView() {
        HBox pills = new HBox(4);
        pills.getStyleClass().add("category-pill-buttons");
        for (BrowseOptions.ClassificationOption option : BrowseOptions.PROJECT_TYPES) {
            addCategory(pills, option);
        }
        pillIndicator.getStyleClass().add("category-pill-indicator");
        pillIndicator.setMouseTransparent(true);
        pillIndicator.setVisible(false);
        pillIndicator.setMinWidth(0);
        pillIndicator.setMinHeight(36);
        pillIndicator.setPrefHeight(36);
        pillIndicator.setMaxWidth(0);
        pillIndicator.setMaxHeight(36);
        pillIndicator.setScaleX(1);
        StackPane pillShell = new StackPane(pillIndicator, pills);
        pillShell.getStyleClass().add("category-pills");
        pillShell.setMinHeight(46);
        pillShell.setPrefHeight(46);
        pillShell.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(pillIndicator, Pos.CENTER_LEFT);
        StackPane.setAlignment(pills, Pos.CENTER_LEFT);

        ScrollPane categoryScroll = new ScrollPane(pillShell);
        categoryScroll.getStyleClass().add("category-scroll");
        categoryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        categoryScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        categoryScroll.setFitToHeight(true);
        categoryScroll.setPannable(true);
        categoryScroll.setMinWidth(0);
        categoryScroll.setPrefHeight(48);
        scrollSupport.configure(categoryScroll, true);
        configureFades(categoryScroll);

        StackPane frame = new StackPane(categoryScroll, leftFade, rightFade);
        frame.getStyleClass().add("category-scroll-frame");
        frame.setMinWidth(0);
        frame.setPrefHeight(48);
        StackPane.setAlignment(categoryScroll, Pos.CENTER_LEFT);
        StackPane.setAlignment(leftFade, Pos.CENTER_LEFT);
        StackPane.setAlignment(rightFade, Pos.CENTER_RIGHT);
        HBox.setHgrow(frame, Priority.NEVER);
        pillShell.layoutBoundsProperty().addListener((observable, oldValue, bounds) ->
                updateContentWidth(frame, bounds.getWidth()));
        Platform.runLater(() -> updateContentWidth(frame, pillShell.getLayoutBounds().getWidth()));
        Platform.runLater(this::refresh);
        return frame;
    }

    private void addCategory(HBox pane, BrowseOptions.ClassificationOption option) {
        Button button = new Button();
        button.getStyleClass().add("pill");
        HBox content = new HBox(8, LauncherIcons.icon(option.icon(), 15),
                spacedTitleText(option.label(), "pill-label", 14));
        content.setAlignment(Pos.CENTER);
        content.setMouseTransparent(true);
        button.setGraphic(content);
        button.setOnAction(event -> selectClassification(option));
        categoryButtons.put(option, button);
        pane.getChildren().add(button);
    }

    private Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private Node spacedTitleText(String value, String styleClass, double fontSize) {
        HBox text = new HBox();
        text.getStyleClass().add(styleClass);
        text.setAlignment(Pos.CENTER);
        for (char letter : value.toCharArray()) {
            Label letterLabel = text(String.valueOf(letter), styleClass + "-letter");
            letterLabel.setFont(Font.font("Arial Black", FontWeight.EXTRA_BOLD, fontSize));
            text.getChildren().add(letterLabel);
        }
        return text;
    }

    private void configureFades(ScrollPane categoryScroll) {
        leftFade.getStyleClass().setAll("category-edge-fade", "left");
        rightFade.getStyleClass().setAll("category-edge-fade", "right");
        for (Region fade : List.of(leftFade, rightFade)) {
            fade.setMouseTransparent(true);
            fade.setMinWidth(42);
            fade.setPrefWidth(42);
            fade.setMaxWidth(42);
            fade.setMaxHeight(Double.MAX_VALUE);
            fade.setVisible(false);
        }

        categoryScroll.hvalueProperty().addListener((observable, oldValue, newValue) -> updateFades(categoryScroll));
        categoryScroll.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> updateFades(categoryScroll));
        categoryScroll.getContent().layoutBoundsProperty().addListener((observable, oldValue, newValue) -> updateFades(categoryScroll));
        Platform.runLater(() -> updateFades(categoryScroll));
    }

    private void updateFades(ScrollPane categoryScroll) {
        double contentWidth = categoryScroll.getContent().getLayoutBounds().getWidth();
        double viewportWidth = categoryScroll.getViewportBounds().getWidth();
        boolean overflow = contentWidth > viewportWidth + OVERFLOW_TOLERANCE;
        overflowing.set(overflow);
        double hValue = categoryScroll.getHvalue();
        leftFade.setVisible(overflow && hValue > 0.01);
        rightFade.setVisible(overflow && hValue < 0.99);
    }

    private void updateContentWidth(Region frame, double width) {
        if (!Double.isFinite(width) || width <= 0) {
            return;
        }
        double nextWidth = Math.ceil(width);
        contentWidth.set(nextWidth);
        frame.setPrefWidth(nextWidth + VIEWPORT_CHROME_BUFFER);
        frame.setMaxWidth(nextWidth + VIEWPORT_CHROME_BUFFER);
    }

    private void animatePill() {
        Button selected = categoryButtons.getOrDefault(
                selectedClassification,
                categoryButtons.get(BrowseOptions.ClassificationOption.defaultOption())
        );
        if (selected == null) {
            return;
        }
        if (selected.getWidth() <= 0) {
            Platform.runLater(this::animatePill);
            return;
        }
        pillIndicator.setVisible(true);
        double targetX = selected.getBoundsInParent().getMinX();
        double targetWidth = selected.getWidth();
        if (pillTimeline != null) {
            pillTimeline.stop();
        }
        if (pillIndicator.getPrefWidth() <= 0) {
            pillIndicator.setTranslateX(targetX);
            pillIndicator.setMinWidth(targetWidth);
            pillIndicator.setPrefWidth(targetWidth);
            pillIndicator.setMaxWidth(targetWidth);
            pillIndicator.setScaleX(1);
            return;
        }
        double startWidth = Math.max(1, pillIndicator.getBoundsInParent().getWidth());
        double startX = pillIndicator.getBoundsInParent().getMinX();
        double startScale = startWidth / targetWidth;
        double startTranslate = startX - (targetWidth - startWidth) / 2.0;
        pillIndicator.setMinWidth(targetWidth);
        pillIndicator.setPrefWidth(targetWidth);
        pillIndicator.setMaxWidth(targetWidth);
        pillIndicator.setTranslateX(startTranslate);
        pillIndicator.setScaleX(startScale);
        pillTimeline = new Timeline(new KeyFrame(PILL_TRANSITION_DURATION,
                new KeyValue(pillIndicator.translateXProperty(), targetX, PILL_EASE),
                new KeyValue(pillIndicator.scaleXProperty(), 1, PILL_EASE)));
        pillTimeline.play();
    }
}
