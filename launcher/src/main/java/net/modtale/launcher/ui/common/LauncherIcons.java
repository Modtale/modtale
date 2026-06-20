package net.modtale.launcher.ui.common;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

public final class LauncherIcons {

    public enum Glyph {
        ALIGN_JUSTIFY("M3 6h18 M3 12h18 M3 18h18"),
        ARROW_BIG_DOWN("M15 5H9v6H4.16a1 1 0 0 0-.82 1.57l8.84 9.58a1 1 0 0 0 1.48 0l8.84-9.58A1 1 0 0 0 21.84 11H17V5a2 2 0 0 0-2-2Z"),
        ARROW_BIG_UP("M9 19h6v-6h4.84a1 1 0 0 0 .82-1.57l-8.84-9.58a1 1 0 0 0-1.48 0L2.34 11.43A1 1 0 0 0 3.16 13H8v6a2 2 0 0 0 2 2Z"),
        BELL("M10.3 21a2 2 0 0 0 3.4 0 M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"),
        BOOK_OPEN("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2Z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7Z"),
        BOX("M21 8a2 2 0 0 0-1-1.73L12 2 4 6.27A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73L12 22l8-4.27A2 2 0 0 0 21 16Z M3.3 7 12 12l8.7-5 M12 22V12"),
        CALENDAR("M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z"),
        CHECK("M20 6 9 17l-5-5"),
        ALERT_CIRCLE("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M12 8v4 M12 16h.01"),
        ALERT_TRIANGLE("M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z M12 9v4 M12 17h.01"),
        ARROW_RIGHT("M5 12h14 M12 5l7 7-7 7"),
        CHEVRON_DOWN("m6 9 6 6 6-6"),
        CHEVRON_LEFT("m15 18-6-6 6-6"),
        CHEVRON_RIGHT("m9 18 6-6-6-6"),
        CHEVRON_UP("m18 15-6-6-6 6"),
        CIRCLE("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z"),
        CLOCK("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M12 6v6l4 2"),
        CODE("m16 18 6-6-6-6 M8 6l-6 6 6 6"),
        COPY("M8 8h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2Z M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"),
        CORNER_DOWN_LEFT("m9 10-5 5 5 5 M20 4v7a4 4 0 0 1-4 4H4"),
        CORNER_DOWN_RIGHT("m15 10 5 5-5 5 M4 4v7a4 4 0 0 0 4 4h12"),
        DATABASE("M3 6c0 2 4 4 9 4s9-2 9-4-4-4-9-4-9 2-9 4Z M3 6v6c0 2 4 4 9 4s9-2 9-4V6 M3 12v6c0 2 4 4 9 4s9-2 9-4v-6"),
        DOWNLOAD("M12 3v12 M7 10l5 5 5-5 M5 21h14"),
        EDIT("M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4Z"),
        EXTERNAL_LINK("M15 3h6v6 M10 14 21 3 M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"),
        FILE_CODE("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z M14 2v6h6 M10 13l-2 2 2 2 M14 17l2-2-2-2"),
        FILTER("M22 3H2l8 9.46V19l4 2v-8.54Z"),
        FLAG("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z M4 22V15"),
        FLAME("M8.5 14.5A4.5 4.5 0 1 0 16 11c0-4-4-6-4-9-3 2-5 5-5 9a5 5 0 0 0 1.5 3.5Z"),
        GEAR("M12.2 2h-.4a2 2 0 0 0-2 2v.2a2 2 0 0 1-1 1.7l-.4.2a2 2 0 0 1-2 0l-.2-.1a2 2 0 0 0-2.7.7l-.2.4A2 2 0 0 0 4 9.8l.2.1a2 2 0 0 1 1 1.7v.6a2 2 0 0 1-1 1.7l-.2.1a2 2 0 0 0-.7 2.7l.2.4a2 2 0 0 0 2.7.7l.2-.1a2 2 0 0 1 2 0l.4.2a2 2 0 0 1 1 1.7v.4a2 2 0 0 0 2 2h.4a2 2 0 0 0 2-2v-.2a2 2 0 0 1 1-1.7l.4-.2a2 2 0 0 1 2 0l.2.1a2 2 0 0 0 2.7-.7l.2-.4a2 2 0 0 0-.7-2.7l-.2-.1a2 2 0 0 1-1-1.7v-.5a2 2 0 0 1 1-1.7l.2-.1a2 2 0 0 0 .7-2.7l-.2-.4a2 2 0 0 0-2.7-.7l-.2.1a2 2 0 0 1-2 0l-.4-.2a2 2 0 0 1-1-1.7V4a2 2 0 0 0-2-2Z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"),
        GEM("M6 3h12l4 6-10 12L2 9Z M2 9h20 M6 3l6 18 6-18"),
        GLOBE("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M2 12h20 M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z"),
        GRID("M3 3h7v7H3Z M14 3h7v7h-7Z M14 14h7v7h-7Z M3 14h7v7H3Z"),
        HASH("M4 9h16 M4 15h16 M10 3 8 21 M16 3l-2 18"),
        HEART("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z"),
        IMAGE("M21 15V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11 M21 15l-5-5L5 21 M14 14l2-2 5 5 M8.5 8.5h.01"),
        INFO("M12 16v-4 M12 8h.01 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z"),
        LAYERS("m12 2 9 5-9 5-9-5Z M3 12l9 5 9-5 M3 17l9 5 9-5"),
        LAYOUT("M3 3h18v18H3Z M3 9h18 M9 21V9"),
        LINK("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71 M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"),
        LIST("M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01"),
        LOADER_2("M21 12a9 9 0 1 1-6.219-8.56"),
        LOG_OUT("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9"),
        MAXIMIZE("M8 3H5a2 2 0 0 0-2 2v3 M16 3h3a2 2 0 0 1 2 2v3 M21 16v3a2 2 0 0 1-2 2h-3 M8 21H5a2 2 0 0 1-2-2v-3"),
        MESSAGE_SQUARE("M21 15a4 4 0 0 1-4 4H7l-4 4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z"),
        MINUS("M5 12h14"),
        PALETTE("M12 22a10 10 0 1 1 10-10c0 2-1.5 3-3.5 3H17a2 2 0 0 0 0 4h.5c-1.6 1.9-3.6 3-5.5 3Z M6.5 11.5h.01 M9.5 7.5h.01 M14.5 7.5h.01 M17.5 11.5h.01"),
        PACKAGE_PLUS("M16 16h6 M19 13v6 M21 8a2 2 0 0 0-1-1.73L12 2 4 6.27A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73L12 22l3-1.6 M3.3 7 12 12l8.7-5 M12 22V12"),
        REFRESH_CW("M21 12a9 9 0 0 0-9-9 9.8 9.8 0 0 0-6.7 2.7L3 8 M3 3v5h5 M3 12a9 9 0 0 0 9 9 9.8 9.8 0 0 0 6.7-2.7L21 16 M16 16h5v5"),
        RESTORE("M8 3h11a2 2 0 0 1 2 2v11 M3 8h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z"),
        ROTATE_CCW("M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5"),
        SAVE("M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z M17 21v-8H7v8 M7 3v5h8"),
        SCALE("M16 16l3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z M2 16l3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z M7 21h10 M12 3v18 M3 7h2c2 0 5-1 7-4 2 3 5 4 7 4h2"),
        SEARCH("M21 21l-4.35-4.35 M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16Z"),
        SEND("m22 2-7 20-4-9-9-4 20-7Z M22 2 11 13"),
        SHARE_2("M18 2a3 3 0 1 1 0 6a3 3 0 1 1 0-6 M6 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6 M18 16a3 3 0 1 1 0 6a3 3 0 1 1 0-6 M8.59 13.51l6.83 3.98 M15.41 6.51l-6.82 3.98"),
        SLIDERS("M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4"),
        STAR("M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21 7 14.2 2 9.3l6.9-1Z"),
        TAG("M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8Z M7.5 7.5h.01"),
        TRASH("M3 6h18 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6"),
        USER("M20 21a8 8 0 0 0-16 0 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"),
        X("M18 6 6 18 M6 6l12 12"),
        ZAP("M13 2 3 14h8l-1 8 10-12h-8Z");

        private final String path;

        Glyph(String path) {
            this.path = path;
        }
    }

    public enum BrandGlyph {
        DISCORD(127.14, 96.36, new BrandPath[]{
                new BrandPath("M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c2.36-24.44-4.2-48.62-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z", "#5865F2")
        }),
        GITHUB(24, 24, new BrandPath[]{
                new BrandPath("M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z", "#ffffff")
        }),
        GITLAB(24, 24, new BrandPath[]{
                new BrandPath("M22.65 14.39L12 22.13L1.35 14.39L4.74 3.99C4.82 3.73 5.12 3.63 5.33 3.82L8.99 7.5L12 10.5L15.01 7.5L18.67 3.82C18.88 3.63 19.18 3.73 19.26 3.99L22.65 14.39Z", "#FC6D26")
        }),
        GOOGLE(32, 32, new BrandPath[]{
                new BrandPath("M23.75,16A7.7446,7.7446,0,0,1,8.7177,18.6259L4.2849,22.1721A13.244,13.244,0,0,0,29.25,16", "#00ac47"),
                new BrandPath("M23.75,16a7.7387,7.7387,0,0,1-3.2516,6.2987l4.3824,3.5059A13.2042,13.2042,0,0,0,29.25,16", "#4285f4"),
                new BrandPath("M8.25,16a7.698,7.698,0,0,1,.4677-2.6259L4.2849,9.8279a13.177,13.177,0,0,0,0,12.3442l4.4328-3.5462A7.698,7.698,0,0,1,8.25,16Z", "#ffba00"),
                new BrandPath("M16,8.25a7.699,7.699,0,0,1,4.558,1.4958l4.06-3.7893A13.2152,13.2152,0,0,0,4.2849,9.8279l4.4328,3.5462A7.756,7.756,0,0,1,16,8.25Z", "#ea4435"),
                new BrandPath("M29.25,15v1L27,19.5H16.5V14H28.25A1,1,0,0,1,29.25,15Z", "#4285f4")
        }),
        TWITTER(24, 24, new BrandPath[]{
                new BrandPath("M23.954 4.569c-.885.392-1.83.656-2.825.775a4.932 4.932 0 0 0 2.163-2.723 9.864 9.864 0 0 1-3.127 1.195 4.916 4.916 0 0 0-8.38 4.482A13.944 13.944 0 0 1 1.671 3.149a4.916 4.916 0 0 0 1.523 6.557 4.897 4.897 0 0 1-2.228-.616v.06a4.918 4.918 0 0 0 3.946 4.827 4.996 4.996 0 0 1-2.212.085 4.923 4.923 0 0 0 4.604 3.417A9.867 9.867 0 0 1 0 19.54a13.93 13.93 0 0 0 7.548 2.212c9.057 0 14.01-7.503 14.01-14.01 0-.213-.005-.425-.014-.636a10.012 10.012 0 0 0 2.46-2.548Z", "#ffffff")
        }),
        FACEBOOK(24, 24, new BrandPath[]{
                new BrandPath("M22.675 0H1.325C.593 0 0 .593 0 1.326v21.348C0 23.407.593 24 1.325 24h11.495v-9.294H9.692v-3.622h3.128V8.413c0-3.1 1.893-4.788 4.659-4.788 1.325 0 2.463.099 2.795.143v3.24l-1.918.001c-1.504 0-1.795.715-1.795 1.763v2.312h3.587l-.467 3.622h-3.12V24h6.116C23.407 24 24 23.407 24 22.674V1.326C24 .593 23.407 0 22.675 0Z", "#ffffff")
        });

        private final double viewBoxWidth;
        private final double viewBoxHeight;
        private final BrandPath[] paths;

        BrandGlyph(double viewBoxWidth, double viewBoxHeight, BrandPath[] paths) {
            this.viewBoxWidth = viewBoxWidth;
            this.viewBoxHeight = viewBoxHeight;
            this.paths = paths;
        }
    }

    private LauncherIcons() {
    }

    public static Node icon(Glyph glyph, double size) {
        SVGPath path = new SVGPath();
        path.setContent(glyph.path);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        path.getStyleClass().add("svg-icon");
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);

        StackPane pane = new StackPane(path);
        pane.getStyleClass().add("icon-wrap");
        pane.setAlignment(Pos.CENTER);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    public static Node brandIcon(BrandGlyph glyph, double size) {
        Group group = new Group();
        for (BrandPath brandPath : glyph.paths) {
            SVGPath path = new SVGPath();
            path.setContent(brandPath.path);
            path.setFill(Color.web(brandPath.fill));
            group.getChildren().add(path);
        }
        double scale = size / Math.max(glyph.viewBoxWidth, glyph.viewBoxHeight);
        group.setScaleX(scale);
        group.setScaleY(scale);

        StackPane pane = new StackPane(group);
        pane.getStyleClass().add("brand-icon-wrap");
        pane.setAlignment(Pos.CENTER);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        return pane;
    }

    private record BrandPath(String path, String fill) {
    }
}
