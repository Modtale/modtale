package net.modtale.controller.project;

import tools.jackson.databind.ObjectMapper;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.service.user.AccountService;
import net.modtale.service.project.WikiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiProxyControllerTest {

    private WikiProxyController controller;
    private WikiService wikiService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        wikiService = mock(WikiService.class);
        accountService = mock(AccountService.class);
        controller = new WikiProxyController(wikiService, accountService);
    }

    @Test
    void getWikiPageExtractsTheTrailingPagePath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/wiki/project-1/guides/getting-started");
        var payload = new ObjectMapper().readTree("{\"title\":\"Getting Started\"}");

        when(wikiService.getWikiPage("project-1", "guides/getting-started", null)).thenReturn(payload);

        var response = controller.getWikiPage("project-1", request, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Getting Started", response.getBody().get("title").asText());
        verify(wikiService).getWikiPage("project-1", "guides/getting-started", null);
    }

    @Test
    void getWikiPageRejectsMalformedRequestUris() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/not-wiki/project-1/page");

        assertThrows(IllegalArgumentException.class, () -> controller.getWikiPage("project-1", request, null));
    }

    @Test
    void handleWikiUpstreamUsesProblemDetailFormatting() {
        UpstreamServiceException error = new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Wiki upstream is unavailable.");

        var response = controller.handleWikiUpstream(error);

        assertEquals(502, response.getStatusCode().value());
        assertTrue(response.getBody().getDetail().contains("Wiki upstream is unavailable."));
    }
}
