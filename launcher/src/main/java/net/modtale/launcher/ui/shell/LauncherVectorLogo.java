package net.modtale.launcher.ui.shell;

import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Renders the site logo as paths at the launcher window's actual pixel scale. */
public final class LauncherVectorLogo {
    private static final String RESOURCE = "/net/modtale/launcher/ui/nativefx/assets/logo_light.svg";
    private static final Pattern TRANSFORM = Pattern.compile("(translate|matrix)\\(([^)]*)\\)");

    private LauncherVectorLogo() { }

    public static Node create(double height) {
        try (InputStream input = Objects.requireNonNull(LauncherVectorLogo.class.getResourceAsStream(RESOURCE))) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            Group artwork = group(factory.newDocumentBuilder().parse(input).getDocumentElement());
            Bounds source = artwork.getBoundsInLocal();
            double scale = height / source.getHeight();
            artwork.getTransforms().add(new Scale(scale, scale));
            Bounds scaled = artwork.getBoundsInParent();
            artwork.setTranslateX(-scaled.getMinX());
            artwork.setTranslateY(-scaled.getMinY());
            artwork.setMouseTransparent(true);

            Pane viewport = new Pane(artwork);
            viewport.setMinSize(scaled.getWidth(), height);
            viewport.setPrefSize(scaled.getWidth(), height);
            viewport.setMaxSize(scaled.getWidth(), height);
            return viewport;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not render the launcher logo", ex);
        }
    }

    private static Group group(Element element) {
        Group result = new Group();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) continue;
            String name = child.getLocalName() == null ? child.getTagName() : child.getLocalName();
            if ("path".equals(name)) {
                SVGPath path = new SVGPath();
                path.setContent(child.getAttribute("d"));
                path.setFill(fill(child.getAttribute("style")));
                result.getChildren().add(path);
            } else if ("g".equals(name)) {
                result.getChildren().add(group(child));
            }
        }
        applyTransform(result, element.getAttribute("transform"));
        return result;
    }

    private static Color fill(String style) {
        for (String declaration : style.split(";")) {
            String[] pair = declaration.split(":", 2);
            if (pair.length == 2 && "fill".equals(pair[0].trim())) {
                return Color.web(pair[1].trim());
            }
        }
        return Color.BLACK;
    }

    private static void applyTransform(Group group, String value) {
        Matcher matcher = TRANSFORM.matcher(value);
        while (matcher.find()) {
            String[] parts = matcher.group(2).trim().split("[,\\s]+");
            if ("translate".equals(matcher.group(1))) {
                group.getTransforms().add(new Translate(Double.parseDouble(parts[0]),
                        parts.length > 1 ? Double.parseDouble(parts[1]) : 0));
            } else if (parts.length == 6) {
                Affine affine = new Affine();
                affine.setMxx(Double.parseDouble(parts[0]));
                affine.setMyx(Double.parseDouble(parts[1]));
                affine.setMxy(Double.parseDouble(parts[2]));
                affine.setMyy(Double.parseDouble(parts[3]));
                affine.setTx(Double.parseDouble(parts[4]));
                affine.setTy(Double.parseDouble(parts[5]));
                group.getTransforms().add(affine);
            }
        }
    }
}
