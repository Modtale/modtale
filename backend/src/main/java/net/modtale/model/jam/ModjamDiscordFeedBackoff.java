package net.modtale.model.jam;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "modjam_discord_feed_backoff")
public record ModjamDiscordFeedBackoff(@Id String id, Instant blockedUntil, Instant activatedAt) {}
