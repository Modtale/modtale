package net.modtale.service.news;

import java.util.*;
import net.modtale.model.news.*;
import net.modtale.repository.news.NewsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewsServiceTest {
    private NewsContent content(String body) { return new NewsContent("Title", "Summary", "Excerpt", "Team", List.of("Update"), "/assets/cover.png", "Cover", body); }
    @Test void draftsStayPrivateUntilExplicitPublicationAndUnpublishKeepsDraft() {
        var repo = mock(NewsRepository.class); var service = new NewsService(repo);
        var post = new NewsArticle(); post.slug="test"; post.version=2L; post.draft=content("<p>Old</p>"); post.published=post.draft; post.publishedAt="2026-09-01T00:00:00Z";
        when(repo.findById("test")).thenReturn(Optional.of(post)); when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service.save("test", 2L, content("<p>New draft</p>"));
        assertEquals("<p>Old</p>", service.publicPost(post).body());
        service.publish("test", 2L); assertEquals("<p>New draft</p>", service.publicPost(post).body());
        assertEquals("2026-09-01T00:00:00Z", post.publishedAt);
        service.unpublish("test", 2L); assertNull(post.published); assertNotNull(post.draft);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.publicPost(post)).getStatusCode().value());
    }
    @Test void staleEditsCannotOverwriteCurrentDraftOrPublish() {
        var repo=mock(NewsRepository.class); var service=new NewsService(repo); var post=new NewsArticle(); post.version=4L;
        when(repo.findById("test")).thenReturn(Optional.of(post));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.save("test", 3L, content("<p>Edit</p>"))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.publish("test", 3L)).getStatusCode().value());
        verify(repo, never()).save(any());
    }
    @Test void sanitizesScriptsAndUnsafeUrlsButPreservesFormattingAndDemos() {
        String html=NewsService.sanitize("<h2 id='creators'>Title</h2><p style='text-align:center;color:#ff0000'><strong>Bold</strong><u>Underline</u></p><table><tr><td colspan='2'>Cell</td></tr></table><div data-demo-clip='modpack-creation' data-demo-alt='Build a pack'></div><video src='/demo.mp4' controls></video><img src='https://example.com/image.gif' onerror='alert(1)'><script>alert(1)</script><a href='javascript:alert(1)'>Link</a>");
        assertFalse(html.contains("<script")); assertFalse(html.contains("onerror")); assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("data-demo-clip=\"modpack-creation\"")); assertTrue(html.contains("<video")); assertTrue(html.contains("<u>")); assertTrue(html.contains("text-align:center")); assertTrue(html.contains("colspan=\"2\"")); assertTrue(html.contains("id=\"creators\""));
    }
    @Test void validatesSlugMediaAndPublicationReadiness() {
        var repo=mock(NewsRepository.class); var service=new NewsService(repo); when(repo.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.save("bad/slug", null, content("<p>Text</p>")));
        var unsafe=new NewsContent("Title", "Summary", "", "Team", List.of(), "javascript:alert(1)", "Cover", "<p>Text</p>");
        assertThrows(ResponseStatusException.class, () -> service.save("safe", null, unsafe));
        var post=new NewsArticle(); post.version=1L; post.draft=content("<p></p>"); when(repo.findById("safe")).thenReturn(Optional.of(post));
        assertThrows(ResponseStatusException.class, () -> service.publish("safe", 1L));
    }
}
