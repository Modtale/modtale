package net.modtale.launcher.ui.project;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeMarkdownRenderer {

    private static final double MAX_BLOCK_IMAGE_WIDTH = 1080;
    private static final double MAX_INLINE_IMAGE_WIDTH = 480;
    private static final Pattern HTML_ALIGNMENT = Pattern.compile(
            "(?is)(?:align\\s*=\\s*['\"]?\\s*(left|center|right|justify)|text-align\\s*:\\s*(left|center|right|justify))");
    private static final Pattern HTML_IFRAME_SRC = Pattern.compile(
            "(?is)<iframe\\b[^>]*\\bsrc\\s*=\\s*(['\"])(.*?)\\1[^>]*>");
    private static final Pattern HTML_IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern HTML_SRC = Pattern.compile("(?is)\\bsrc\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern HTML_ALT = Pattern.compile("(?is)\\balt\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern HTML_TITLE = Pattern.compile(
            "(?is)\\btitle\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern HTML_DANGEROUS_CONTENT = Pattern.compile(
            "(?is)<(script|style|object|embed|svg)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern YOUTUBE_VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final List<String> JVM_KEYWORDS = List.of(
            "abstract", "as", "break", "case", "catch", "class", "const", "continue", "data", "default",
            "do", "else", "enum", "extends", "false", "final", "finally", "for", "fun", "if",
            "implements", "import", "in", "instanceof", "interface", "is", "new", "null", "object",
            "override", "package", "private", "protected", "public", "return", "sealed", "static", "super",
            "switch", "this", "throw", "throws", "true", "try", "val", "var", "void", "when", "while"
    );

    private static final Parser PARSER = Parser.builder(new MutableDataSet()
                    .set(Parser.EXTENSIONS, List.of(
                            StrikethroughExtension.create(),
                            AutolinkExtension.create(),
                            TablesExtension.create(),
                            TaskListExtension.create()
                    )))
            .build();

    private final CachedImageLoader imageLoader;
    private final Consumer<String> openUrl;

    NativeMarkdownRenderer(CachedImageLoader imageLoader, Consumer<String> openUrl) {
        this.imageLoader = imageLoader;
        this.openUrl = openUrl;
    }

    VBox render(String content) {
        VBox prose = new VBox(0);
        prose.getStyleClass().add("project-detail-prose");
        Document document = PARSER.parse(normalizeContent(content));
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            javafx.scene.Node rendered = renderBlock(child, prose);
            if (rendered != null) {
                prose.getChildren().add(rendered);
            }
        }
        if (prose.getChildren().isEmpty()) {
            prose.getChildren().add(paragraph("No description."));
        }
        return prose;
    }

    private javafx.scene.Node renderBlock(Node node, VBox prose) {
        if (node instanceof Heading heading) {
            return heading(heading);
        }
        if (node instanceof Paragraph paragraph) {
            return paragraph(paragraph, prose);
        }
        if (node instanceof BulletList list) {
            return list(list, false, prose);
        }
        if (node instanceof OrderedList list) {
            return list(list, true, prose);
        }
        if (node instanceof BlockQuote quote) {
            return blockQuote(quote, prose);
        }
        if (node instanceof FencedCodeBlock code) {
            return codeBlock(code.getContentChars().toString(), code.getInfo().toString());
        }
        if (node instanceof IndentedCodeBlock code) {
            return codeBlock(code.getContentChars().toString(), "text");
        }
        if (node instanceof TableBlock table) {
            return table(table);
        }
        if (node instanceof ThematicBreak) {
            Region rule = new Region();
            rule.getStyleClass().add("project-detail-markdown-hr");
            return rule;
        }
        if (node instanceof HtmlBlock html) {
            return htmlBlock(html.getChars().toString(), prose);
        }
        return null;
    }

    private javafx.scene.Node heading(Heading heading) {
        TextFlow flow = inlineFlow("project-detail-prose-h" + Math.min(6, heading.getLevel()));
        appendInlineChildren(flow, heading, InlineStyle.PLAIN);
        return selectableTextFlow(flow);
    }

    private javafx.scene.Node paragraph(Paragraph paragraph, VBox prose) {
        MarkdownImage image = blockImage(paragraph);
        if (image != null) {
            return blockImage(image, prose);
        }
        TextFlow flow = inlineFlow("project-detail-prose-p");
        appendInlineChildren(flow, paragraph, InlineStyle.PLAIN);
        return selectableTextFlow(flow);
    }

    private javafx.scene.Node paragraph(String text) {
        TextFlow flow = inlineFlow("project-detail-prose-p");
        flow.getChildren().add(text(text, InlineStyle.PLAIN));
        return selectableTextFlow(flow);
    }

    private TextFlow inlineFlow(String styleClass) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add(styleClass);
        flow.setMaxWidth(Double.MAX_VALUE);
        return flow;
    }

    private javafx.scene.Node list(Node list, boolean ordered, VBox prose) {
        VBox box = new VBox(6);
        box.getStyleClass().add(ordered ? "project-detail-prose-ordered-list" : "project-detail-prose-list");
        int index = 1;
        if (list instanceof OrderedList orderedList) {
            index = Math.max(1, orderedList.getStartNumber());
        }
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListItem item) {
                box.getChildren().add(listItem(item, ordered ? index + "." : "•", prose));
                index += 1;
            }
        }
        return box;
    }

    private javafx.scene.Node listItem(ListItem item, String markerText, VBox prose) {
        ParsedTask task = parseTask(item);
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("project-detail-markdown-list-row");

        Region marker;
        if (task.task()) {
            CheckBox checkBox = new CheckBox();
            checkBox.getStyleClass().add("project-detail-markdown-task");
            checkBox.setSelected(task.checked());
            checkBox.setDisable(true);
            marker = checkBox;
        } else {
            Label markerLabel = new Label(markerText);
            markerLabel.getStyleClass().add("project-detail-prose-bullet");
            marker = markerLabel;
        }

        VBox content = new VBox(6);
        content.setMinWidth(0);
        HBox.setHgrow(content, Priority.ALWAYS);
        for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
            javafx.scene.Node rendered;
            if (child instanceof Paragraph paragraph) {
                rendered = listParagraph(paragraph, task);
                task = ParsedTask.NONE;
            } else if (child instanceof BulletList || child instanceof OrderedList) {
                rendered = renderBlock(child, prose);
            } else {
                rendered = renderBlock(child, prose);
            }
            if (rendered != null) {
                content.getChildren().add(rendered);
            }
        }
        if (content.getChildren().isEmpty() && !task.text().isBlank()) {
            content.getChildren().add(paragraph(task.text()));
        }

        row.getChildren().addAll(marker, content);
        return row;
    }

    private javafx.scene.Node listParagraph(Paragraph paragraph, ParsedTask task) {
        TextFlow flow = inlineFlow("project-detail-prose-li");
        if (task.task()) {
            String text = textContent(paragraph).replaceFirst("^\\s*\\[[ xX]]\\s*", "");
            flow.getChildren().add(text(text, InlineStyle.PLAIN));
        } else {
            appendInlineChildren(flow, paragraph, InlineStyle.PLAIN);
        }
        return selectableTextFlow(flow);
    }

    private javafx.scene.Node blockQuote(BlockQuote quote, VBox prose) {
        VBox box = new VBox(10);
        box.getStyleClass().add("project-detail-markdown-blockquote");
        for (Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
            javafx.scene.Node rendered = renderBlock(child, prose);
            if (rendered != null) {
                box.getChildren().add(rendered);
            }
        }
        return box;
    }

    private javafx.scene.Node codeBlock(String content, String language) {
        String lang = language == null || language.isBlank() ? "text" : language.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        VBox block = new VBox(0);
        block.getStyleClass().add("project-detail-markdown-code-block");
        HBox header = new HBox();
        header.getStyleClass().add("project-detail-markdown-code-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label langLabel = new Label(lang);
        langLabel.getStyleClass().add("project-detail-markdown-code-language");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String codeContent = content.stripTrailing();
        Button copy = new Button("Copy", LauncherIcons.icon(LauncherIcons.Glyph.COPY, 13));
        copy.getStyleClass().add("project-detail-markdown-code-copy");
        copy.setMnemonicParsing(false);
        copy.setCursor(Cursor.HAND);
        copy.setFocusTraversable(false);
        copy.setOnAction(event -> copyCodeBlock(copy, codeContent));
        header.getChildren().addAll(langLabel, spacer, copy);

        TextFlow code = highlightedCode(codeContent, lang);
        double longestLine = Arrays.stream(codeContent.split("\n", -1))
                .mapToInt(String::length)
                .max()
                .orElse(0);
        code.setPrefWidth(Math.max(1, longestLine * 8.2));
        StackPane codeCanvas = new StackPane(code);
        codeCanvas.getStyleClass().add("project-detail-markdown-code-canvas");
        codeCanvas.setAlignment(Pos.TOP_LEFT);
        codeCanvas.setMinWidth(code.getPrefWidth() + 32);
        codeCanvas.setPrefWidth(code.getPrefWidth() + 32);
        ScrollPane body = horizontalScroller(codeCanvas, "project-detail-markdown-code-body");
        body.setPrefViewportHeight(Math.max(52, codeContent.lines().count() * 21 + 32));
        block.getChildren().addAll(header, body);
        return block;
    }

    private ScrollPane horizontalScroller(javafx.scene.Node content, String styleClass) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add(styleClass);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        scroll.setMinWidth(0);
        scroll.setMaxWidth(Double.MAX_VALUE);
        if (content instanceof Region region) {
            double contentHeight = region.prefHeight(-1);
            if (Double.isFinite(contentHeight) && contentHeight > 0) {
                scroll.setPrefViewportHeight(contentHeight);
            }
        }
        return scroll;
    }

    private javafx.scene.Node selectableTextFlow(TextFlow flow) {
        Path selection = new Path();
        selection.getStyleClass().add("project-detail-markdown-selection");
        selection.setManaged(false);
        selection.setMouseTransparent(true);

        StackPane wrapper = new StackPane(selection, flow);
        wrapper.getStyleClass().add("project-detail-markdown-selectable");
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setFocusTraversable(true);
        wrapper.setMinWidth(0);
        wrapper.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(selection, Pos.TOP_LEFT);
        StackPane.setAlignment(flow, Pos.TOP_LEFT);

        int[] selectionStart = {-1};
        int[] selectionEnd = {-1};

        wrapper.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            int index = hitInsertionIndex(flow, event);
            selectionStart[0] = index;
            selectionEnd[0] = index;
            selection.getElements().clear();
            wrapper.requestFocus();
        });
        wrapper.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown() || selectionStart[0] < 0) {
                return;
            }
            selectionEnd[0] = hitInsertionIndex(flow, event);
            updateSelection(selection, flow, selectionStart[0], selectionEnd[0]);
            event.consume();
        });
        wrapper.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
                String selected = selectedText(flow, selectionStart[0], selectionEnd[0]);
                if (!selected.isEmpty()) {
                    ClipboardContent clipboard = new ClipboardContent();
                    clipboard.putString(selected);
                    Clipboard.getSystemClipboard().setContent(clipboard);
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                selectionStart[0] = -1;
                selectionEnd[0] = -1;
                selection.getElements().clear();
                event.consume();
            }
        });
        wrapper.widthProperty().addListener((observable, previous, current) ->
                updateSelection(selection, flow, selectionStart[0], selectionEnd[0]));
        flow.layoutBoundsProperty().addListener((observable, previous, current) ->
                updateSelection(selection, flow, selectionStart[0], selectionEnd[0]));

        return wrapper;
    }

    private int hitInsertionIndex(TextFlow flow, MouseEvent event) {
        HitInfo hit = flow.hitTest(flow.sceneToLocal(event.getSceneX(), event.getSceneY()));
        return Math.max(0, hit.getInsertionIndex());
    }

    private void updateSelection(Path selection, TextFlow flow, int anchor, int caret) {
        if (anchor < 0 || caret < 0 || anchor == caret) {
            selection.getElements().clear();
            return;
        }
        int start = Math.min(anchor, caret);
        int end = Math.max(anchor, caret);
        PathElement[] shape = flow.rangeShape(start, end);
        selection.getElements().setAll(Arrays.asList(shape));
    }

    private String selectedText(TextFlow flow, int anchor, int caret) {
        if (anchor < 0 || caret < 0 || anchor == caret) {
            return "";
        }
        String text = textFlowContent(flow);
        int start = Math.max(0, Math.min(Math.min(anchor, caret), text.length()));
        int end = Math.max(0, Math.min(Math.max(anchor, caret), text.length()));
        return start >= end ? "" : text.substring(start, end);
    }

    private String textFlowContent(TextFlow flow) {
        StringBuilder builder = new StringBuilder();
        for (javafx.scene.Node child : flow.getChildren()) {
            if (child instanceof Text text) {
                builder.append(text.getText());
            } else if (child instanceof Label label) {
                builder.append(label.getText());
            }
        }
        return builder.toString();
    }

    private void copyCodeBlock(Button copy, String content) {
        ClipboardContent clipboard = new ClipboardContent();
        clipboard.putString(content == null ? "" : content);
        Clipboard.getSystemClipboard().setContent(clipboard);
        copy.setText("Copied");
        copy.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13));

        PauseTransition reset = new PauseTransition(Duration.millis(1600));
        reset.setOnFinished(event -> {
            copy.setText("Copy");
            copy.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.COPY, 13));
        });
        reset.play();
    }

    private TextFlow highlightedCode(String content, String lang) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("project-detail-markdown-code-text");
        String normalized = content == null ? "" : content;
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            appendHighlightedLine(flow, lines[i], lang);
            if (i < lines.length - 1) {
                appendToken(flow, "\n", "plain");
            }
        }
        return flow;
    }

    private void appendHighlightedLine(TextFlow flow, String line, String lang) {
        if ("json".equals(lang) || "jsonc".equals(lang)) {
            appendJsonLine(flow, line);
        } else if ("java".equals(lang) || "kotlin".equals(lang) || "kt".equals(lang)) {
            appendJvmLine(flow, line);
        } else {
            appendToken(flow, line, "plain");
        }
    }

    private void appendJsonLine(TextFlow flow, String line) {
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (current == '"') {
                int end = stringEnd(line, index, '"');
                String token = line.substring(index, end);
                appendToken(flow, token, nextNonSpace(line, end) == ':' ? "property" : "string");
                index = end;
            } else if (current == '-' || Character.isDigit(current)) {
                int end = numberEnd(line, index);
                appendToken(flow, line.substring(index, end), "number");
                index = end;
            } else if (startsWord(line, index, "true") || startsWord(line, index, "false") || startsWord(line, index, "null")) {
                int end = wordEnd(line, index);
                appendToken(flow, line.substring(index, end), "keyword");
                index = end;
            } else if ("{}[]:,".indexOf(current) >= 0) {
                appendToken(flow, Character.toString(current), "punctuation");
                index += 1;
            } else {
                appendToken(flow, Character.toString(current), "plain");
                index += 1;
            }
        }
    }

    private void appendJvmLine(TextFlow flow, String line) {
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (index + 1 < line.length() && current == '/' && line.charAt(index + 1) == '/') {
                appendToken(flow, line.substring(index), "comment");
                return;
            }
            if (index + 1 < line.length() && current == '/' && line.charAt(index + 1) == '*') {
                appendToken(flow, line.substring(index), "comment");
                return;
            }
            if (current == '"' || current == '\'') {
                int end = stringEnd(line, index, current);
                appendToken(flow, line.substring(index, end), "string");
                index = end;
            } else if (current == '@') {
                int end = wordEnd(line, index + 1);
                appendToken(flow, line.substring(index, Math.max(index + 1, end)), "annotation");
                index = Math.max(index + 1, end);
            } else if (Character.isDigit(current)) {
                int end = numberEnd(line, index);
                appendToken(flow, line.substring(index, end), "number");
                index = end;
            } else if (Character.isJavaIdentifierStart(current)) {
                int end = wordEnd(line, index);
                String word = line.substring(index, end);
                char next = nextNonSpace(line, end);
                if (JVM_KEYWORDS.contains(word)) {
                    appendToken(flow, word, "keyword");
                } else if (next == '(') {
                    appendToken(flow, word, "function");
                } else {
                    appendToken(flow, word, "plain");
                }
                index = end;
            } else if ("{}[]();,.:=+-*/<>!?".indexOf(current) >= 0) {
                appendToken(flow, Character.toString(current), "punctuation");
                index += 1;
            } else {
                appendToken(flow, Character.toString(current), "plain");
                index += 1;
            }
        }
    }

    private void appendToken(TextFlow flow, String value, String kind) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Text text = new Text(value);
        text.getStyleClass().addAll("project-detail-markdown-code-token", kind);
        flow.getChildren().add(text);
    }

    private static int stringEnd(String line, int start, char quote) {
        int index = start + 1;
        boolean escaped = false;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                return index + 1;
            }
            index += 1;
        }
        return line.length();
    }

    private static int numberEnd(String line, int start) {
        int index = start + 1;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (!Character.isDigit(current) && current != '.' && current != '_' && current != 'e' && current != 'E'
                    && current != '+' && current != '-') {
                break;
            }
            index += 1;
        }
        return index;
    }

    private static int wordEnd(String line, int start) {
        int index = start;
        while (index < line.length() && Character.isJavaIdentifierPart(line.charAt(index))) {
            index += 1;
        }
        return index;
    }

    private static char nextNonSpace(String line, int start) {
        for (int i = start; i < line.length(); i++) {
            char current = line.charAt(i);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private static boolean startsWord(String line, int start, String word) {
        if (!line.regionMatches(start, word, 0, word.length())) {
            return false;
        }
        int end = start + word.length();
        return end == line.length() || !Character.isJavaIdentifierPart(line.charAt(end));
    }

    private javafx.scene.Node table(TableBlock table) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("project-detail-markdown-table");
        int[] rowIndex = {0};
        for (Node child = table.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableHead || child instanceof TableBody) {
                for (Node row = child.getFirstChild(); row != null; row = row.getNext()) {
                    if (row instanceof TableRow tableRow) {
                        addTableRow(grid, tableRow, rowIndex[0], child instanceof TableHead);
                        rowIndex[0] += 1;
                    }
                }
            } else if (child instanceof TableRow tableRow) {
                addTableRow(grid, tableRow, rowIndex[0], false);
                rowIndex[0] += 1;
            }
        }
        ScrollPane scroll = horizontalScroller(grid, "project-detail-markdown-table-scroll");
        double tableHeight = Math.max(48, rowIndex[0] * 40 + 8);
        scroll.setPrefViewportHeight(tableHeight);
        scroll.setMinHeight(tableHeight);
        scroll.setPrefHeight(tableHeight);
        return scroll;
    }

    private void addTableRow(GridPane grid, TableRow row, int rowIndex, boolean header) {
        int col = 0;
        for (Node child = row.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableCell cell) {
                TextFlow flow = inlineFlow(header ? "project-detail-markdown-th" : "project-detail-markdown-td");
                flow.setTextAlignment(switch (cell.getAlignment()) {
                    case CENTER -> TextAlignment.CENTER;
                    case RIGHT -> TextAlignment.RIGHT;
                    default -> TextAlignment.LEFT;
                });
                appendInlineChildren(flow, cell, InlineStyle.PLAIN);
                grid.add(flow, col, rowIndex);
                col += 1;
            }
        }
    }

    private javafx.scene.Node blockImage(MarkdownImage markdownImage, VBox prose) {
        String url = markdownImage.image().getUrl().toString();
        return blockImage(url, markdownImage.alt(), markdownImage.linkUrl(), prose);
    }

    private javafx.scene.Node blockImage(String url, String alt, String linkUrl, VBox prose) {
        if (!isSafeImage(url)) {
            return null;
        }
        ImageView view = new ImageView();
        view.getStyleClass().add("project-detail-markdown-image");
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setAccessibleText(alt == null ? "" : alt);

        HBox shell = new HBox(view);
        shell.getStyleClass().add("project-detail-markdown-image-shell");
        shell.setAlignment(Pos.CENTER_LEFT);
        shell.setMinWidth(0);
        shell.setMaxWidth(Double.MAX_VALUE);

        prose.widthProperty().addListener((observable, previous, current) -> resizeBlockImage(view, prose));
        view.imageProperty().addListener((observable, previous, current) -> {
            if (current != null) {
                current.widthProperty().addListener((imageObservable, oldWidth, newWidth) -> resizeBlockImage(view, prose));
                current.progressProperty().addListener((progressObservable, oldProgress, newProgress) -> resizeBlockImage(view, prose));
            }
            resizeBlockImage(view, prose);
        });
        imageLoader.loadInto(view, url, 0, 0, true);
        makeLink(shell, linkUrl);
        return shell;
    }

    private void resizeBlockImage(ImageView view, VBox prose) {
        double availableWidth = prose.getWidth();
        double maxWidth = Double.isFinite(availableWidth) && availableWidth > 1
                ? Math.min(MAX_BLOCK_IMAGE_WIDTH, availableWidth)
                : MAX_BLOCK_IMAGE_WIDTH;
        javafx.scene.image.Image image = view.getImage();
        double naturalWidth = image == null ? 0 : image.getWidth();
        view.setFitWidth(naturalWidth > 0 ? Math.min(naturalWidth, maxWidth) : maxWidth);
        view.setFitHeight(0);
    }

    private javafx.scene.Node inlineImage(Image image) {
        String url = image.getUrl().toString();
        if (!isSafeImage(url)) {
            return null;
        }
        ImageView view = imageView(url, MAX_INLINE_IMAGE_WIDTH, 0, true);
        view.setAccessibleText(image.getText().toString());
        return view;
    }

    private ImageView imageView(String url, double requestedWidth, double requestedHeight, boolean preserveRatio) {
        ImageView view = new ImageView();
        view.getStyleClass().add("project-detail-markdown-image");
        view.setPreserveRatio(preserveRatio);
        view.setSmooth(true);
        view.setFitWidth(requestedWidth);
        double effectiveRequestedHeight = requestedHeight > 0 ? requestedHeight : requestedWidth * 2;
        imageLoader.loadInto(view, url, requestedWidth * 1.5, effectiveRequestedHeight, preserveRatio);
        return view;
    }

    private MarkdownImage blockImage(Node node) {
        if (!(node instanceof Paragraph paragraph)) {
            return null;
        }
        Node first = paragraph.getFirstChild();
        if (first instanceof Image image && first.getNext() == null) {
            return new MarkdownImage(image, "");
        }
        if (first instanceof Link link && first.getNext() == null) {
            Node linked = link.getFirstChild();
            if (linked instanceof Image image && linked.getNext() == null) {
                return new MarkdownImage(image, link.getUrl().toString());
            }
        }
        return null;
    }

    private void appendInlineChildren(TextFlow flow, Node parent, InlineStyle style) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(flow, child, style);
        }
    }

    private void appendInline(TextFlow flow, Node node, InlineStyle style) {
        if (node instanceof com.vladsch.flexmark.ast.Text textNode) {
            flow.getChildren().add(text(textNode.getChars().toString(), style));
            return;
        }
        if (node instanceof SoftLineBreak) {
            flow.getChildren().add(text(" ", style));
            return;
        }
        if (node instanceof HardLineBreak) {
            flow.getChildren().add(text("\n", style));
            return;
        }
        if (node instanceof Code code) {
            Label label = new Label(code.getText().toString());
            label.getStyleClass().add("project-detail-markdown-inline-code");
            flow.getChildren().add(label);
            return;
        }
        if (node instanceof Emphasis) {
            appendInlineChildren(flow, node, style.withItalic());
            return;
        }
        if (node instanceof StrongEmphasis) {
            appendInlineChildren(flow, node, style.withBold());
            return;
        }
        if (node instanceof Strikethrough) {
            appendInlineChildren(flow, node, style.withStrike());
            return;
        }
        if (node instanceof Link link) {
            appendLink(flow, link, style);
            return;
        }
        if (node instanceof AutoLink autoLink) {
            appendAutoLink(flow, autoLink, style);
            return;
        }
        if (node instanceof Image image) {
            javafx.scene.Node rendered = inlineImage(image);
            if (rendered != null) {
                flow.getChildren().add(rendered);
            }
            return;
        }
        if (node instanceof HtmlInline html) {
            String sanitized = sanitizeHtml(html.getChars().toString());
            if (!sanitized.isBlank()) {
                flow.getChildren().add(text(sanitized, style));
            }
            return;
        }
        appendInlineChildren(flow, node, style);
    }

    private void appendLink(TextFlow flow, Link link, InlineStyle style) {
        String url = link.getUrl().toString();
        int start = flow.getChildren().size();
        appendInlineChildren(flow, link, style.withLink());
        for (int i = start; i < flow.getChildren().size(); i++) {
            javafx.scene.Node child = flow.getChildren().get(i);
            makeLink(child, url);
        }
    }

    private void appendAutoLink(TextFlow flow, AutoLink link, InlineStyle style) {
        String label = link.getText().toString();
        String target = label.contains("@") && !label.contains("://") ? "mailto:" + label : label;
        Text rendered = text(label, style.withLink());
        makeLink(rendered, target);
        flow.getChildren().add(rendered);
    }

    private void makeLink(javafx.scene.Node node, String url) {
        if (!isSafeLink(url)) return;
        node.setOnMouseClicked(event -> openUrl.accept(url));
        node.setCursor(Cursor.HAND);
    }

    private javafx.scene.Node htmlBlock(String html, VBox prose) {
        Matcher iframe = HTML_IFRAME_SRC.matcher(html == null ? "" : html);
        if (iframe.find()) {
            String videoId = youtubeVideoId(decodeEntities(iframe.group(2)));
            if (videoId != null) {
                Matcher title = HTML_TITLE.matcher(iframe.group());
                return youtubeEmbed(videoId, title.find() ? decodeEntities(title.group(2)) : "YouTube video");
            }
        }
        Matcher image = HTML_IMAGE.matcher(html == null ? "" : html);
        if (image.find()) {
            Matcher src = HTML_SRC.matcher(image.group());
            Matcher alt = HTML_ALT.matcher(image.group());
            if (src.find()) {
                return blockImage(decodeEntities(src.group(2)),
                        alt.find() ? decodeEntities(alt.group(2)) : "", "", prose);
            }
        }
        String sanitized = sanitizeHtml(html);
        if (sanitized.isBlank()) return null;
        TextFlow flow = inlineFlow(htmlHeadingStyle(html));
        flow.setTextAlignment(htmlAlignment(html));
        flow.getChildren().add(text(sanitized, InlineStyle.PLAIN));
        return selectableTextFlow(flow);
    }

    private javafx.scene.Node youtubeEmbed(String videoId, String title) {
        StackPane media = new StackPane();
        media.getStyleClass().add("project-detail-markdown-youtube");
        media.setMinHeight(220);
        media.setPrefHeight(360);
        media.setMaxHeight(480);
        media.setCursor(Cursor.HAND);

        ImageView preview = imageView("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg", 960, 540, false);
        preview.fitWidthProperty().bind(media.widthProperty());
        preview.fitHeightProperty().bind(media.heightProperty());
        Label play = new Label("Watch on YouTube", LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 16));
        play.getStyleClass().add("project-detail-markdown-youtube-action");
        media.getChildren().addAll(preview, play);
        media.setAccessibleText(title == null || title.isBlank() ? "YouTube video" : title);
        media.setOnMouseClicked(event -> openUrl.accept("https://www.youtube.com/watch?v=" + videoId));
        return media;
    }

    static String youtubeVideoId(String rawUrl) {
        try {
            URI uri = new URI(rawUrl == null ? "" : rawUrl.trim());
            String scheme = lower(uri.getScheme());
            String host = lower(uri.getHost());
            if (!("http".equals(scheme) || "https".equals(scheme)) || host == null) return null;
            if (host.startsWith("www.")) host = host.substring(4);
            if (host.startsWith("m.")) host = host.substring(2);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String candidate = null;
            if ("youtu.be".equals(host)) {
                candidate = firstPathSegment(path);
            } else if ("youtube.com".equals(host) || "youtube-nocookie.com".equals(host)) {
                for (String prefix : List.of("/embed/", "/shorts/", "/live/")) {
                    if (path.startsWith(prefix)) candidate = firstPathSegment(path.substring(prefix.length()));
                }
                if ("/watch".equals(path) && uri.getRawQuery() != null) {
                    for (String part : uri.getRawQuery().split("&")) {
                        if (part.startsWith("v=")) candidate = part.substring(2);
                    }
                }
            }
            return candidate != null && YOUTUBE_VIDEO_ID.matcher(candidate).matches() ? candidate : null;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String firstPathSegment(String path) {
        String value = path == null ? "" : path.replaceFirst("^/+", "");
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    static boolean isSafeLink(String rawUrl) {
        try {
            URI uri = new URI(rawUrl == null ? "" : rawUrl.trim());
            String scheme = lower(uri.getScheme());
            return "http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    static boolean isSafeImage(String rawUrl) {
        try {
            String value = rawUrl == null ? "" : rawUrl.trim();
            URI uri = new URI(value);
            String scheme = lower(uri.getScheme());
            return scheme == null ? !value.isBlank() : "http".equals(scheme) || "https".equals(scheme);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    static String htmlHeadingStyle(String html) {
        Matcher heading = Pattern.compile("(?is)^\\s*<h([1-6])\\b").matcher(html == null ? "" : html);
        return heading.find() ? "project-detail-prose-h" + heading.group(1) : "project-detail-prose-p";
    }

    static TextAlignment htmlAlignment(String html) {
        Matcher matcher = HTML_ALIGNMENT.matcher(html == null ? "" : html);
        if (!matcher.find()) return TextAlignment.LEFT;
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "center" -> TextAlignment.CENTER;
            case "right" -> TextAlignment.RIGHT;
            case "justify" -> TextAlignment.JUSTIFY;
            default -> TextAlignment.LEFT;
        };
    }

    private Text text(String value, InlineStyle style) {
        Text text = new Text(decodeEntities(value));
        text.getStyleClass().add("project-detail-markdown-text");
        if (style.bold()) {
            text.getStyleClass().add("strong");
        }
        if (style.italic()) {
            text.getStyleClass().add("em");
        }
        if (style.strike()) {
            text.setStrikethrough(true);
        }
        if (style.link()) {
            text.getStyleClass().add("link");
        }
        return text;
    }

    private ParsedTask parseTask(ListItem item) {
        if (item instanceof TaskListItem taskItem) {
            return new ParsedTask(true, taskItem.isItemDoneMarker(), textContent(item));
        }
        String text = textContent(item);
        if (text.matches("^\\s*\\[[ xX]]\\s+.*")) {
            return new ParsedTask(true, text.matches("^\\s*\\[[xX]]\\s+.*"), text.replaceFirst("^\\s*\\[[ xX]]\\s+", ""));
        }
        return ParsedTask.NONE;
    }

    private String textContent(Node node) {
        List<String> parts = new ArrayList<>();
        collectText(node, parts);
        return String.join("", parts).trim();
    }

    private void collectText(Node node, List<String> parts) {
        if (node instanceof com.vladsch.flexmark.ast.Text text) {
            parts.add(text.getChars().toString());
        } else if (node instanceof Code code) {
            parts.add(code.getText().toString());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            parts.add(" ");
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectText(child, parts);
        }
    }

    private static String normalizeContent(String content) {
        String normalized = content == null || content.isBlank() ? "*No description.*" : content;
        return normalized.replace("\r\n", "\n").trim();
    }

    static String sanitizeHtml(String value) {
        String withoutDangerousContent = HTML_DANGEROUS_CONTENT.matcher(value == null ? "" : value).replaceAll("");
        return decodeEntities(withoutDangerousContent)
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p\\s*>", "\n")
                .replaceAll("(?is)</(?:div|li|blockquote|h[1-6])\\s*>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .trim();
    }

    static boolean parsesAutolink(String content) {
        Document document = PARSER.parse(content == null ? "" : content);
        for (Node node : document.getDescendants()) {
            if (node instanceof AutoLink) return true;
        }
        return false;
    }

    private static String decodeEntities(String value) {
        return value == null ? "" : value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private record InlineStyle(boolean bold, boolean italic, boolean strike, boolean link) {
        static final InlineStyle PLAIN = new InlineStyle(false, false, false, false);

        InlineStyle withBold() {
            return new InlineStyle(true, italic, strike, link);
        }

        InlineStyle withItalic() {
            return new InlineStyle(bold, true, strike, link);
        }

        InlineStyle withStrike() {
            return new InlineStyle(bold, italic, true, link);
        }

        InlineStyle withLink() {
            return new InlineStyle(bold, italic, strike, true);
        }
    }

    private record MarkdownImage(Image image, String alt, String linkUrl) {
        private MarkdownImage(Image image, String linkUrl) {
            this(image, image == null ? "" : image.getText().toString(), linkUrl);
        }

        private MarkdownImage {
            alt = alt == null ? "" : alt;
            linkUrl = linkUrl == null ? "" : linkUrl;
        }
    }

    private record ParsedTask(boolean task, boolean checked, String text) {
        static final ParsedTask NONE = new ParsedTask(false, false, "");
    }
}
