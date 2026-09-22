package net.modtale.launcher;

/** Runs without a display, before the native bootstrap starts the application. */
public final class RuntimeProbe {
    public static void main(String[] args) throws Exception {
        int version = Runtime.version().feature();
        if (version < 25 || version > 26) {
            throw new IllegalStateException("Modtale requires a validated Java 25 or 26 runtime");
        }
        for (String module : new String[] { "java.desktop", "java.logging", "java.net.http",
                "java.prefs", "java.xml", "jdk.httpserver", "jdk.unsupported" }) {
            if (ModuleLayer.boot().findModule(module).isEmpty()) {
                throw new IllegalStateException("Missing Java module: " + module);
            }
        }
        java.security.KeyPairGenerator.getInstance("EC");
        javax.net.ssl.SSLContext.getDefault();
        Class.forName("javafx.scene.control.Control", false, RuntimeProbe.class.getClassLoader());
        Class.forName("com.sun.jna.Native");
    }
}
