package net.modtale.launcher.ui.common;

import javafx.event.EventTarget;
import javafx.scene.Node;

public final class LauncherOverlaySupport {

    private LauncherOverlaySupport() {
    }

    public static boolean eventTargetInside(EventTarget target, Node root) {
        if (!(target instanceof Node node) || root == null) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == root) {
                return true;
            }
        }
        return false;
    }

    public static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
