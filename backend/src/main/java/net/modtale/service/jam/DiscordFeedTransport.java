package net.modtale.service.jam;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface DiscordFeedTransport {
    Result send(URI destination, Map<String, Object> payload);

    record Result(int status, Duration retryAfter) {
        public boolean accepted() { return status >= 200 && status < 300; }
    }
}
