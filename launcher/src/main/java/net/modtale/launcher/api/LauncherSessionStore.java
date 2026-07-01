package net.modtale.launcher.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class LauncherSessionStore {

    private static final Duration MAX_SESSION_AGE = Duration.ofDays(7);
    private static final Set<String> SESSION_COOKIE_NAMES = Set.of("SESSION", "JSESSIONID", "XSRF-TOKEN");

    private final Path sessionPath;
    private final ObjectMapper mapper = new ObjectMapper();

    LauncherSessionStore(Path sessionPath) {
        this.sessionPath = sessionPath;
    }

    synchronized boolean loadInto(CookieStore cookieStore, URI baseUri) {
        if (sessionPath == null || !Files.exists(sessionPath)) {
            return false;
        }

        try {
            List<StoredCookie> storedCookies = mapper.readValue(
                    sessionPath.toFile(),
                    new TypeReference<>() {
                    }
            );
            Instant now = Instant.now();
            int loaded = 0;
            for (StoredCookie storedCookie : storedCookies) {
                Optional<HttpCookie> cookie = storedCookie.toHttpCookie(now);
                if (cookie.isPresent()) {
                    cookieStore.add(storedCookie.originUri(baseUri), cookie.get());
                    loaded++;
                }
            }
            if (loaded == 0) {
                clear(cookieStore);
                return false;
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            clear(cookieStore);
            return false;
        }
    }

    synchronized void saveFrom(CookieStore cookieStore, URI baseUri) {
        if (sessionPath == null) {
            return;
        }

        Instant now = Instant.now();
        List<StoredCookie> storedCookies = cookieStore.get(baseUri).stream()
                .filter(LauncherSessionStore::isSessionCookie)
                .flatMap(cookie -> StoredCookie.from(baseUri, cookie, now).stream())
                .toList();

        if (storedCookies.isEmpty()) {
            clear(cookieStore);
            return;
        }

        try {
            Path parent = sessionPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(sessionPath.toFile(), storedCookies);
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not save launcher session to " + sessionPath, ex);
        }
    }

    synchronized void clear(CookieStore cookieStore) {
        cookieStore.removeAll();
        if (sessionPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(sessionPath);
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not clear launcher session from " + sessionPath, ex);
        }
    }

    boolean hasSessionFile() {
        return sessionPath != null && Files.exists(sessionPath);
    }

    private static boolean isSessionCookie(HttpCookie cookie) {
        return cookie != null
                && SESSION_COOKIE_NAMES.contains(cookie.getName().toUpperCase(Locale.ROOT))
                && !cookie.hasExpired();
    }

    private static final class StoredCookie {
        public String name;
        public String value;
        public String domain;
        public String path;
        public boolean secure;
        public boolean httpOnly;
        public int version;
        public String portList;
        public String originUri;
        public long expiresAtEpochSecond;

        public StoredCookie() {
        }

        private static Optional<StoredCookie> from(URI baseUri, HttpCookie cookie, Instant now) {
            if (!isSessionCookie(cookie)) {
                return Optional.empty();
            }

            long maxAge = cookie.getMaxAge();
            long ttlSeconds = maxAge < 0
                    ? MAX_SESSION_AGE.toSeconds()
                    : Math.min(maxAge, MAX_SESSION_AGE.toSeconds());
            if (ttlSeconds <= 0) {
                return Optional.empty();
            }

            StoredCookie storedCookie = new StoredCookie();
            storedCookie.name = cookie.getName();
            storedCookie.value = cookie.getValue();
            storedCookie.domain = cookie.getDomain();
            storedCookie.path = cookie.getPath();
            storedCookie.secure = cookie.getSecure();
            storedCookie.httpOnly = cookie.isHttpOnly();
            storedCookie.version = cookie.getVersion();
            storedCookie.portList = cookie.getPortlist();
            storedCookie.originUri = baseUri.toString();
            storedCookie.expiresAtEpochSecond = now.plusSeconds(ttlSeconds).getEpochSecond();
            return Optional.of(storedCookie);
        }

        private Optional<HttpCookie> toHttpCookie(Instant now) {
            if (name == null || name.isBlank() || value == null) {
                return Optional.empty();
            }

            long remainingSeconds = expiresAtEpochSecond - now.getEpochSecond();
            if (remainingSeconds <= 0) {
                return Optional.empty();
            }

            HttpCookie cookie = new HttpCookie(name, value);
            if (domain != null && !domain.isBlank()) {
                cookie.setDomain(domain);
            }
            cookie.setPath(path == null || path.isBlank() ? "/" : path);
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
            cookie.setVersion(version);
            cookie.setMaxAge(remainingSeconds);
            if (portList != null && !portList.isBlank()) {
                cookie.setPortlist(portList);
            }
            return Optional.of(cookie);
        }

        private URI originUri(URI fallback) {
            if (originUri == null || originUri.isBlank()) {
                return fallback;
            }
            try {
                return URI.create(originUri);
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
    }
}
