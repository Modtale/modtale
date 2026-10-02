package net.modtale.repository.jam;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.modtale.model.jam.ModjamDiscordEvent;

public interface ModjamDiscordOutboxStore {
    void enqueue(ModjamDiscordEvent event);
    Optional<ModjamDiscordEvent> claimNext(Instant now, Duration lease);
    void delivered(ModjamDiscordEvent event, Instant now);
    void retry(ModjamDiscordEvent event, Instant nextAttempt, Integer status);
    void discard(ModjamDiscordEvent event);
    void pauseUntil(Instant until);
    Instant activationTime(Instant now);
}
