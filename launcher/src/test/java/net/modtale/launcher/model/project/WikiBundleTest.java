package net.modtale.launcher.model.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WikiBundleTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsNestedWikiTreeAndPageContent() throws Exception {
        WikiBundle bundle = mapper.readValue("""
                {
                  "metadata": {
                    "mod": {
                      "id": "wiki-1",
                      "name": "LevelingCore",
                      "index": {"slug": "home-1"},
                      "pages": [{
                        "id": "guides",
                        "slug": "guides",
                        "title": "Guides",
                        "children": [{"id": "install", "slug": "guides/install", "title": "Install"}]
                      }]
                    }
                  },
                  "page": {"title": "Getting started", "content": "# Hello\\nDocumentation"},
                  "pageSlug": "guides/install"
                }
                """, WikiBundle.class);

        assertEquals("LevelingCore", bundle.wikiName());
        assertEquals("home-1", bundle.indexSlug());
        assertEquals("Guides", bundle.pages().getFirst().title());
        assertEquals("guides/install", bundle.pages().getFirst().children().getFirst().slug());
        assertTrue(bundle.pages().getFirst().contains("guides/install"));
        assertEquals("Getting started", bundle.content().title());
        assertEquals("# Hello\nDocumentation", bundle.content().content());
    }

    @Test
    void supportsLegacyTopLevelPageTreeShape() throws Exception {
        WikiBundle bundle = mapper.readValue("""
                {
                  "metadata": {
                    "mod": {"id": "wiki-1"},
                    "index": {"slug": "home-1"},
                    "pages": [{"slug": "home-1", "name": "Home"}]
                  },
                  "page": {"title": "Home", "content": ""},
                  "pageSlug": "home-1"
                }
                """, WikiBundle.class);

        assertEquals("home-1", bundle.indexSlug());
        assertEquals("Home", bundle.pages().getFirst().title());
        assertTrue(bundle.content().empty());
    }
}
