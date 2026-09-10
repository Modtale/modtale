package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HytaleAvatarClientTest {
    private static final String SKIN = "413090670192ec90c51408ff1f3f1f28";

    @Test void requestsSavedSkinAndReusesLookupAcrossAvatarSizes() {
        var calls = new AtomicInteger();
        var client = new HytaleAvatarClient(username -> { calls.incrementAndGet(); return SKIN; }, Runnable::run);
        assertEquals("https://hyvatar.io/render/NPC?size=256&skin_id=" + SKIN,
                client.avatarUrl("Villagers654").join());
        assertEquals(client.avatarUrl("Villagers654").join(), client.avatarUrl("villagers654").join());
        assertEquals(1, calls.get());
    }

    @Test void retriesFailedLookupsInsteadOfCachingTheDefaultCharacter() {
        var calls = new AtomicInteger();
        var client = new HytaleAvatarClient(username -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("Unavailable");
            return SKIN;
        }, Runnable::run);
        assertThrows(CompletionException.class, () -> client.avatarUrl("ItsNeil").join());
        assertTrue(client.avatarUrl("ItsNeil").join().endsWith(SKIN));
        assertEquals(2, calls.get());
    }

    @Test void rejectsMissingOrInvalidSkinIdentifiers() {
        var client = new HytaleAvatarClient(username -> "", Runnable::run);
        assertThrows(CompletionException.class, () -> client.avatarUrl("ItsNeil").join());
        assertThrows(CompletionException.class, () -> client.avatarUrl("../unknown").join());
    }
}
