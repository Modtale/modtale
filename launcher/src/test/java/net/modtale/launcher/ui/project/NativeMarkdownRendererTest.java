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
