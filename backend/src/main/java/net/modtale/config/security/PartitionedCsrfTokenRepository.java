package net.modtale.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Preserves CHIPS when Spring Security 7.1's Servlet cookie mapping drops Partitioned. */
final class PartitionedCsrfTokenRepository implements CsrfTokenRepository {
    private final CsrfTokenRepository delegate;

    PartitionedCsrfTokenRepository(CsrfTokenRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return delegate.generateToken(request);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveToken(token, request, new HttpServletResponseWrapper(response) {
            @Override
            public void addCookie(Cookie cookie) {
                ResponseCookie partitioned = ResponseCookie.from(cookie.getName(), cookie.getValue())
                        .path(cookie.getPath())
                        .domain(cookie.getDomain())
                        .secure(cookie.getSecure())
                        .httpOnly(cookie.isHttpOnly())
                        .maxAge(cookie.getMaxAge())
                        .sameSite(cookie.getAttribute("SameSite"))
                        .partitioned(true)
                        .build();
                super.addHeader(HttpHeaders.SET_COOKIE, partitioned.toString());
            }
        });
    }
}
