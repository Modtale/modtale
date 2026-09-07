package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.text.TextAlignment;
import org.junit.jupiter.api.Test;

class NativeMarkdownRendererTest {

    @Test
    void enablesGfmAutolinksAndRestrictsExternalProtocols() {
        assertTrue(NativeMarkdownRenderer.parsesAutolink("Visit https://example.com for details."));
        assertTrue(NativeMarkdownRenderer.isSafeLink("https://example.com/path"));
        assertTrue(NativeMarkdownRenderer.isSafeLink("mailto:creator@example.com"));
        assertFalse(NativeMarkdownRenderer.isSafeLink("javascript:alert(1)"));
        assertFalse(NativeMarkdownRenderer.isSafeLink("file:///etc/passwd"));
        assertTrue(NativeMarkdownRenderer.isSafeImage("https://cdn.example/image.png"));
        assertTrue(NativeMarkdownRenderer.isSafeImage("/assets/image.png"));
        assertFalse(NativeMarkdownRenderer.isSafeImage("file:///etc/passwd"));
    }

    @Test
    void preservesSafeHtmlPresentationWithoutLeakingActiveContent() {
        String html = """
                <div style="text-align: center">Visible<br>copy</div>
                <script>alert('unsafe')</script>
                <style>.hidden { color: red; }</style>
                """;

        assertEquals("Visible\ncopy", NativeMarkdownRenderer.sanitizeHtml(html));
        assertEquals(TextAlignment.CENTER, NativeMarkdownRenderer.htmlAlignment(html));
        assertEquals("project-detail-prose-h4",
                NativeMarkdownRenderer.htmlHeadingStyle("<h4 align=right>Heading</h4>"));
        assertEquals("project-detail-prose-p",
                NativeMarkdownRenderer.htmlHeadingStyle("<h2>Heading</h2><p>Body copy</p>"));
    }

    @Test
    void convertsCurseForgeHtmlListsAndInlineFormattingToMarkdown() {
        String html = """
                <h2>Key Features</h2>
                <ol start="3">
                  <li><strong>Persistent Exploration:</strong> Saved across sessions.</li>
                  <li><em>Cave Mode:</em> Switch underground.</li>
                </ol>
                <ul><li><del>Old</del> New behavior</li></ul>
                """;

        String markdown = NativeMarkdownRenderer.convertCurseForgeHtmlToMarkdown(html);

        assertTrue(markdown.contains("Key Features"), markdown);
        assertTrue(markdown.contains("3. **Persistent Exploration:** Saved across sessions."));
        assertTrue(markdown.contains("4. *Cave Mode:* Switch underground."));
        assertTrue(markdown.contains("* ~~Old~~ New behavior") || markdown.contains("- ~~Old~~ New behavior"));
    }

    @Test
    void convertsNestedCurseForgeListsLinksImagesQuotesTablesAndCode() {
        String html = """
                <blockquote><p>Shared exploration</p></blockquote>
                <ul><li>Parent<ol><li>Nested</li></ol></li></ul>
                <p><a href="https://example.com/docs"><strong>Docs</strong></a><br>
                <img src="https://cdn.example/map.png" alt="Map"></p>
                <table><thead><tr><th>Name</th><th>Value</th></tr></thead>
                <tbody><tr><td>Zoom</td><td>4x</td></tr></tbody></table>
                <pre><code class="language-json">{"ok": true}</code></pre>
                """;

        String markdown = NativeMarkdownRenderer.convertCurseForgeHtmlToMarkdown(html);

        assertTrue(markdown.contains("> Shared exploration"));
        assertTrue(markdown.contains("Parent"));
        assertTrue(markdown.contains("Nested"));
        assertTrue(markdown.contains("[**Docs**](https://example.com/docs)"));
        assertTrue(markdown.contains("![Map](https://cdn.example/map.png)"));
        assertTrue(markdown.contains("Name") && markdown.contains("Value") && markdown.contains("Zoom") && markdown.contains("4x"));
        assertTrue(markdown.contains("{\"ok\": true}"));
    }

    @Test
    void stripsActiveCurseForgeHtmlBeforeConversion() {
        String html = """
                <p>Visible</p>
                <script>alert('unsafe')</script>
                <style>.hidden { display:none }</style>
                <object>payload</object><embed src="https://example.com/x"><svg><text>hidden</text></svg>
                """;

        String markdown = NativeMarkdownRenderer.convertCurseForgeHtmlToMarkdown(html);

        assertTrue(markdown.contains("Visible"));
        assertFalse(markdown.contains("alert"));
        assertFalse(markdown.contains("display:none"));
        assertFalse(markdown.contains("payload"));
        assertFalse(markdown.contains("hidden"));
    }

    @Test
    void leavesPlainCurseForgeMarkdownUntouched() {
        String markdown = "# Heading\n\n1. First\n2. Second\n\n**Bold** and [link](https://example.com).";
        assertEquals(markdown, NativeMarkdownRenderer.convertCurseForgeHtmlToMarkdown(markdown));
        assertFalse(NativeMarkdownRenderer.containsHtml(markdown));
        assertTrue(NativeMarkdownRenderer.containsHtml("<ol><li>First</li></ol>"));
    }

    @Test
    void acceptsOnlyCanonicalSafeYouTubeUrls() {
        assertEquals("dQw4w9WgXcQ",
                NativeMarkdownRenderer.youtubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ",
                NativeMarkdownRenderer.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=12"));
        assertNull(NativeMarkdownRenderer.youtubeVideoId("https://example.com/embed/dQw4w9WgXcQ"));
        assertNull(NativeMarkdownRenderer.youtubeVideoId("javascript:dQw4w9WgXcQ"));
    }
}
