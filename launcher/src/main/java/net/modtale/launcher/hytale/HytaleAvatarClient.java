package net.modtale.launcher.hytale;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.modtale.launcher.wardrobe.WardrobeApiClient;

public final class HytaleAvatarClient {
    private static final long CACHE_MILLIS = 5 * 60_000;
    private final Function<String, String> skinIdLookup;
    private final Executor executor;
    private final ConcurrentHashMap<String, Entry> requests = new ConcurrentHashMap<>();
    private record Entry(long createdAt, CompletableFuture<String> url) {}

    public HytaleAvatarClient(HytaleAuthService auth, Executor executor) {
        var wardrobe = new WardrobeApiClient(auth);
        var mapper = new ObjectMapper();
        this.skinIdLookup = username -> {
            try {
                return mapper.readTree(wardrobe.lookupSkin(username).payload()).path("skinId").asText();
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Invalid avatar skin response", error);
            }
        };
        this.executor = executor;
    }

    HytaleAvatarClient(Function<String, String> skinIdLookup, Executor executor) {
        this.skinIdLookup = skinIdLookup;
        this.executor = executor;
    }

    public CompletableFuture<String> avatarUrl(String username) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[a-zA-Z0-9_]{3,16}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid avatar username"));
        }
        long now = System.currentTimeMillis();
        Entry entry = requests.compute(name.toLowerCase(Locale.ROOT), (key, previous) -> {
            if (previous != null && !previous.url().isCompletedExceptionally()
                    && now - previous.createdAt() < CACHE_MILLIS) return previous;
            return new Entry(now, CompletableFuture.supplyAsync(() -> {
                String skinId = skinIdLookup.apply(name);
                if (skinId == null || !skinId.matches("[a-fA-F0-9]{32}")) {
                    throw new IllegalStateException("No saved avatar skin available");
                }
                return "https://hyvatar.io/render/NPC?size=256&skin_id=" + skinId.toLowerCase(Locale.ROOT);
            }, executor));
        });
        return entry.url();
    }
}
