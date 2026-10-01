package net.modtale.service.jam;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppModjamDiscordFeedProperties;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamDiscordEvent;
import net.modtale.model.jam.ModjamDiscordEvent.Milestone;
import net.modtale.repository.jam.ModjamDiscordOutboxStore;
import net.modtale.repository.jam.ModjamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ModjamDiscordFeedService {
    private static final Logger LOG = LoggerFactory.getLogger(ModjamDiscordFeedService.class);
    private static final List<String> PUBLIC_STATUSES =
            List.of("UPCOMING", "ACTIVE", "VOTING", "AWAITING_WINNERS", "COMPLETED");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final int BATCH_SIZE = 20;
    private final ModjamRepository jams;
    private final ModjamDiscordOutboxStore outbox;
    private final DiscordFeedTransport transport;
    private final AppModjamDiscordFeedProperties properties;
    private final String frontendUrl;
    private final Clock clock;
    private final Instant activationRequestedAt;
    private volatile Instant activatedAt;

    @Autowired
    public ModjamDiscordFeedService(ModjamRepository jams, ModjamDiscordOutboxStore outbox,
            DiscordFeedTransport transport, AppModjamDiscordFeedProperties properties,
            AppFrontendProperties frontend) {
        this(jams, outbox, transport, properties, frontend, Clock.systemUTC());
    }

    ModjamDiscordFeedService(ModjamRepository jams, ModjamDiscordOutboxStore outbox,
            DiscordFeedTransport transport, AppModjamDiscordFeedProperties properties,
            AppFrontendProperties frontend, Clock clock) {
        this.jams = jams;
        this.outbox = outbox;
        this.transport = transport;
        this.properties = properties;
        this.frontendUrl = frontend.url();
        this.clock = clock;
        this.activationRequestedAt = clock.instant();
        if (destination().isPresent()) {
            try {
                activatedAt = outbox.activationTime(activationRequestedAt);
            } catch (RuntimeException exception) {
                LOG.warn("Modjam Discord feed activation deferred");
            }
        }
    }

    /** Call only after the jam is saved. The scheduled reconciliation repairs missed enqueues. */
    public void recordMilestones(Modjam jam) {
        if (destination().isEmpty() || !publicJam(jam)) return;
        Instant now = clock.instant();
        try {
            if (activatedAt == null) activatedAt = outbox.activationTime(activationRequestedAt);
            enqueue(jam, Milestone.CREATED,
                    jam.getPublishedAt() == null ? jam.getCreatedAt() : jam.getPublishedAt(), now);
            if (validSubmissionWindow(jam) && !now.isBefore(jam.getStartDate())) {
                enqueue(jam, Milestone.SUBMISSIONS_OPENED, jam.getStartDate(), now);
            }
            // Concurrent voting starts with submissions, so it has no separate announcement.
            if (separateVotingWindow(jam) && !now.isBefore(jam.getEndDate())) {
                enqueue(jam, Milestone.VOTING_OPENED, jam.getEndDate(), now);
            }
            // An elapsed voting date never announces winners. Only persisted finalization does.
            if ("COMPLETED".equals(jam.getStatus())) {
                enqueue(jam, Milestone.WINNERS_ANNOUNCED, jam.getWinnersAnnouncedAt(), now);
            }
        } catch (RuntimeException exception) {
            // Keep a successful lifecycle action independent of feed storage/delivery failure.
            LOG.warn("Modjam Discord feed enqueue deferred for jam {}", jam.getId());
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcileMilestones() {
        if (destination().isEmpty()) return;
        try {
            jams.findByStatusIn(PUBLIC_STATUSES).forEach(this::recordMilestones);
        } catch (RuntimeException exception) {
            LOG.warn("Modjam Discord feed reconciliation deferred");
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void deliverPending() {
        Optional<URI> destination = destination();
        if (destination.isEmpty()) return;
        for (int i = 0; i < BATCH_SIZE; i++) {
            ModjamDiscordEvent event;
            try {
                event = outbox.claimNext(clock.instant(), LEASE).orElse(null);
            } catch (RuntimeException exception) {
                LOG.warn("Modjam Discord feed outbox temporarily unavailable");
                return;
            }
            if (event == null) return;
            try {
                Optional<Modjam> current = jams.findById(event.getJamId());
                if (current.isEmpty()) {
                    outbox.discard(event);
                    continue;
                }
                Modjam jam = current.get();
                // Re-read before every send: an unpublished jam must never leak queued data.
                if (!publicJam(jam) || !stillApplicable(event, jam, clock.instant())) {
                    outbox.retry(event, clock.instant().plus(Duration.ofMinutes(1)), null);
                    continue;
                }
                DiscordFeedTransport.Result result = transport.send(destination.get(), payload(jam, event));
                if (result.accepted()) {
                    // Discord offers no webhook idempotency key. A process crash after HTTP
                    // acceptance but before this write can replay the message; delivery is at-least-once.
                    outbox.delivered(event, clock.instant());
                } else {
                    retry(event, result.status(), result.retryAfter());
                    // A webhook's rate limit is shared by every event, not just this milestone.
                    if (result.status() == 429) {
                        Duration pause = result.retryAfter() == null ? Duration.ZERO : result.retryAfter();
                        if (pause.compareTo(Duration.ofSeconds(30)) < 0) pause = Duration.ofSeconds(30);
                        outbox.pauseUntil(clock.instant().plus(pause));
                        return;
                    }
                }
            } catch (RuntimeException exception) {
                // Do not log HTTP exception text: it can contain the credential-bearing URL.
                LOG.warn("Modjam Discord feed delivery deferred for event {}", event.getId());
                try {
                    retry(event, null, Duration.ZERO);
                } catch (RuntimeException ignored) {
                    // The durable lease expires and another pass retries after a DB outage.
                    return;
                }
            }
        }
    }

    private void enqueue(Modjam jam, Milestone milestone, Instant occurredAt, Instant now) {
        // Never backfill pre-activation history. The durable cutoff still repairs downtime gaps.
        if (occurredAt == null || activatedAt == null || occurredAt.isBefore(activatedAt) || occurredAt.isAfter(now)) return;
        outbox.enqueue(new ModjamDiscordEvent(jam.getId(), milestone, occurredAt, now));
    }

    private void retry(ModjamDiscordEvent event, Integer status, Duration retryAfter) {
        // Retry indefinitely with a bounded exponential delay, including recoverable config errors.
        long seconds = Math.min(21600, 30L << Math.min(10, Math.max(0, event.getAttempts() - 1)));
        Duration delay = Duration.ofSeconds(seconds);
        if (retryAfter != null && retryAfter.compareTo(delay) > 0) delay = retryAfter;
        outbox.retry(event, clock.instant().plus(delay), status);
    }

    private static boolean publicJam(Modjam jam) {
        return jam != null && jam.getId() != null && jam.getStatus() != null && PUBLIC_STATUSES.contains(jam.getStatus());
    }

    private static boolean validSubmissionWindow(Modjam jam) {
        return jam.getStartDate() != null && jam.getEndDate() != null
                && jam.getEndDate().isAfter(jam.getStartDate());
    }

    private static boolean separateVotingWindow(Modjam jam) {
        return !jam.isAllowConcurrentVoting() && validSubmissionWindow(jam)
                && jam.getVotingEndDate() != null && jam.getVotingEndDate().isAfter(jam.getEndDate());
    }

    private static boolean stillApplicable(ModjamDiscordEvent event, Modjam jam, Instant now) {
        return switch (event.getMilestone()) {
            case CREATED -> true;
            case SUBMISSIONS_OPENED -> validSubmissionWindow(jam) && !now.isBefore(jam.getStartDate());
            case VOTING_OPENED -> separateVotingWindow(jam) && !now.isBefore(jam.getEndDate());
            case WINNERS_ANNOUNCED -> "COMPLETED".equals(jam.getStatus()) && jam.getWinnersAnnouncedAt() != null
                    && !now.isBefore(jam.getWinnersAnnouncedAt());
        };
    }

    private Map<String, Object> payload(Modjam jam, ModjamDiscordEvent event) {
        String action = switch (event.getMilestone()) {
            case CREATED -> "New modjam";
            case SUBMISSIONS_OPENED -> "Submissions opened";
            case VOTING_OPENED -> "Voting opened";
            case WINNERS_ANNOUNCED -> "Winners announced";
        };
        String title = jam.getTitle() == null ? "Modjam" : jam.getTitle()
                .replaceAll("[\\p{Cntrl}\\r\\n]+", " ").replace("@", "@\u200B")
                .replace("<", "").replace(">", "").replace("`", "");
        title = title.substring(0, Math.min(240, title.length()));
        String base = frontendUrl == null ? "https://modtale.net" : frontendUrl.replaceAll("/+$", "");
        String slug = jam.getSlug();
        if (slug == null || !slug.matches("[a-z0-9-]{1,50}")) slug = "";
        String link = slug.isEmpty() ? base + "/jams" : base + "/jam/" + slug;
        String content = action + ": " + title + "\n" + link;
        if (content.length() > 1900) content = content.substring(0, 1900);
        return Map.of("content", content, "allowed_mentions",
                Map.of("parse", List.of(), "users", List.of(), "roles", List.of(), "replied_user", false));
    }

    private Optional<URI> destination() {
        if (!properties.enabled() || properties.url() == null || properties.url().isBlank()) return Optional.empty();
        try {
            URI uri = URI.create(properties.url());
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || uri.getHost() == null
                    || !List.of("discord.com", "canary.discord.com", "ptb.discord.com", "discordapp.com").contains(uri.getHost())
                    || !uri.getPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]+/[A-Za-z0-9._-]+")) {
                return Optional.empty();
            }
            // wait=true asks Discord to confirm message persistence before acknowledging delivery.
            return Optional.of(UriComponentsBuilder.fromUri(uri).replaceQueryParam("wait", "true").build(true).toUri());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
