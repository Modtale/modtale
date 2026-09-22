package net.modtale.launcher.ui.common;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

public final class LauncherTooltips {
    private static final Object ATTACHED = new Object();
    private LauncherTooltips() {}

    public static void install(javafx.scene.Node node, Tooltip tooltip) {
        Tooltip previous = attached(node);
        if (previous != null) Tooltip.uninstall(node, previous);
        node.getProperties().put(ATTACHED, tooltip);
        Tooltip.install(node, tooltip);
    }

    public static void uninstall(javafx.scene.Node node, Tooltip tooltip) {
        if (attached(node) == tooltip) node.getProperties().remove(ATTACHED);
        Tooltip.uninstall(node, tooltip);
    }

    static Tooltip attached(javafx.scene.Node node) {
        return (Tooltip) node.getProperties().get(ATTACHED);
    }

    public static void install(Control control, String description) {
        Tooltip tooltip = create();
        tooltip.setText(description);
        control.setTooltip(tooltip);
        control.accessibleHelpProperty().unbind();
        control.setAccessibleHelp(description);
    }

    public static void install(Control control, ObservableValue<String> description) {
        Tooltip tooltip = create();
        tooltip.textProperty().bind(description);
        control.setTooltip(tooltip);
        control.accessibleHelpProperty().unbind();
        control.accessibleHelpProperty().bind(description);
    }

    private static Tooltip create() {
        Tooltip tooltip = new Tooltip();
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);
        return tooltip;
    }
}
