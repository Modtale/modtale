package net.modtale.controller.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CsrfControllerTest {
    @Test
    void returnsCurrentTokenAndPreventsCaching() {
        var response = new CsrfController().token(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token-value"));
        assertNotNull(response.getBody());
        assertEquals("token-value", response.getBody().token());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
