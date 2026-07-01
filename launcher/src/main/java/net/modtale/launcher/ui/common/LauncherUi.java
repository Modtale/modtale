package net.modtale.launcher.ui.common;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.ProjectClassification;

public final class LauncherUi {

    private LauncherUi() {
    }

    public static VBox panel(String title, String subtitle) {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("glass-panel");
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("panel-subtitle");
        panel.getChildren().addAll(heading, sub);
        return panel;
    }

    public static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(98);
        labelColumn.setPrefWidth(118);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
        return grid;
    }

    public static void addField(GridPane grid, int row, String label, Node field) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("field-label");
        grid.add(labelNode, 0, row);
        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        GridPane.setFillWidth(field, true);
    }

    public static void styleInput(TextField... fields) {
        for (TextField field : fields) {
            field.getStyleClass().add("input");
        }
    }

    public static void styleCombo(ComboBox<?>... combos) {
        for (ComboBox<?> combo : combos) {
            combo.getStyleClass().add("select");
            combo.setMaxWidth(Double.MAX_VALUE);
        }
    }

    public static Button primaryButton(String label) {
        Button button = new Button(label);
        button.getStyleClass().addAll("btn", "primary");
        return button;
    }

    public static Button secondaryButton(String label) {
        Button button = new Button(label);
        button.getStyleClass().addAll("btn", "secondary");
        return button;
    }

    public static Button dangerButton(String label) {
        Button button = new Button(label);
        button.getStyleClass().addAll("btn", "danger");
        return button;
    }

    public static Node statusDot() {
        Region dot = new Region();
        dot.getStyleClass().add("status-dot");
        return dot;
    }

    public static StackPane miniIcon(String text) {
        Label label = new Label(text);
        StackPane pane = new StackPane(label);
        pane.getStyleClass().add("row-icon");
        return pane;
    }

    public static Node toggleCard(CheckBox checkBox) {
        StackPane card = new StackPane(checkBox);
        card.getStyleClass().add("toggle-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    public static Node metricCard(String label, Label value) {
        VBox card = new VBox(6);
        card.getStyleClass().add("metric-card");
        Label title = new Label(label);
        title.getStyleClass().add("metric-label");
        value.getStyleClass().add("metric-value");
        card.getChildren().addAll(title, value);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    public static VBox emptyState(String title, String subtitle) {
        VBox box = new VBox(8);
        box.getStyleClass().add("empty-state");
        box.setAlignment(javafx.geometry.Pos.CENTER);
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("empty-subtitle");
        box.getChildren().addAll(heading, sub);
        return box;
    }

    public static HBox rowCard(String title, String subtitle) {
        HBox row = new HBox(12);
        row.getStyleClass().add("row-card");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        StackPane icon = miniIcon("M");
        VBox copy = new VBox(4);
        Label titleLabel = new Label(value(title, "Untitled Project"));
        titleLabel.getStyleClass().add("row-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("row-subtitle");
        copy.getChildren().addAll(titleLabel, subtitleLabel);
        HBox.setHgrow(copy, Priority.ALWAYS);
        row.getChildren().addAll(icon, copy);
        return row;
    }

    public static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    public static String readableField(String fieldName) {
        return switch (fieldName) {
            case "hytaleModsPath" -> "Hytale mods folder";
            case "hytaleGamePath" -> "Hytale game folder";
            case "hytaleUserDataPath" -> "Hytale user data folder";
            case "hytaleJavaPath" -> "Java executable";
            default -> "path";
        };
    }

    public static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String classificationLabel(String classification) {
        return ProjectClassification.labelFor(classification);
    }

    public static void pseudo(Node node, String pseudoClass, boolean active) {
        node.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(pseudoClass), active);
    }
}
