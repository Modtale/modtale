package net.modtale.controller.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/v1/auth/csrf")
    public ResponseEntity<TokenResponse> token(CsrfToken token) {
        // Trusted cross-origin frontends cannot read the API host's cookie directly.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TokenResponse(token.getToken()));
    }

    public record TokenResponse(String token) {
    }
}
