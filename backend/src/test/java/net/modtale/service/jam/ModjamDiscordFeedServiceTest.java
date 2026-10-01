package net.modtale.service.jam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppModjamDiscordFeedProperties;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamDiscordEvent;
import net.modtale.model.jam.ModjamDiscordEvent.Milestone;
import net.modtale.model.jam.ModjamDiscordEvent.State;
import net.modtale.repository.jam.ModjamDiscordOutboxStore;
import net.modtale.repository.jam.ModjamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

class ModjamDiscordFeedServiceTest {
    // This placeholder is only passed to fake/mock transport; no test opens network sockets.
    private static final String FAKE_DESTINATION = "https://discord.com/api/webhooks/1/test-only-placeholder";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final MemoryOutbox outbox = new MemoryOutbox();
    private final ModjamRepository jams = mock(ModjamRepository.class);
    private final DiscordFeedTransport transport = mock(DiscordFeedTransport.class);
    private Modjam jam;
    private ModjamDiscordFeedService service;

    @BeforeEach
    void setUp() {
        jam = new Modjam();
        jam.setId("jam-test-1");
        jam.setSlug("test-jam");
        jam.setTitle("Test jam");
        jam.setCreatedAt(clock.instant().minusSeconds(3600));
        jam.setPublishedAt(clock.instant());
        jam.setUpdatedAt(clock.instant());
        jam.setStartDate(clock.instant().plusSeconds(60));
        jam.setEndDate(clock.instant().plusSeconds(120));
        jam.setVotingEndDate(clock.instant().plusSeconds(180));
        when(jams.findById(jam.getId())).thenReturn(Optional.of(jam));
        when(jams.findByStatusIn(any())).thenAnswer(invocation -> "DRAFT".equals(jam.getStatus()) ? List.of() : List.of(jam));
        when(transport.send(any(), any())).thenReturn(new DiscordFeedTransport.Result(200, Duration.ZERO));
        service = service(true, FAKE_DESTINATION);
    }

    @Test
    void draftIsSecretUntilPublishedAndCreationIsDeduplicated() {
        service.recordMilestones(jam);
        service.reconcileMilestones();
        service.deliverPending();
        assertTrue(outbox.events.isEmpty());
        verifyNoInteractions(transport);

        jam.setStatus("UPCOMING");
        service.recordMilestones(jam);
        service.recordMilestones(jam);
        service.reconcileMilestones();
        assertEquals(List.of(Milestone.CREATED), outbox.milestones());
        service.deliverPending();
        service.reconcileMilestones();
        service(true, FAKE_DESTINATION).deliverPending();
        verify(transport, times(1)).send(any(), any());
        assertEquals(State.DELIVERED, outbox.events.values().iterator().next().getState());
    }

    @Test
    void openingsAndWinnerAnnouncementSurviveRestartsAndRepeatedSchedulerPasses() {
        jam.setStatus("UPCOMING");
        service.reconcileMilestones();
        clock.advance(Duration.ofSeconds(60));
        service.reconcileMilestones();
        assertEquals(List.of(Milestone.CREATED, Milestone.SUBMISSIONS_OPENED), outbox.milestones());
        clock.advance(Duration.ofSeconds(60));
        service.reconcileMilestones();
        clock.advance(Duration.ofSeconds(60));
        jam.setStatus("AWAITING_WINNERS");
        service.reconcileMilestones();
        assertEquals(3, outbox.events.size());
        assertFalse(outbox.milestones().contains(Milestone.WINNERS_ANNOUNCED));
        jam.setStatus("COMPLETED");
        jam.setUpdatedAt(clock.instant());
        jam.setWinnersAnnouncedAt(clock.instant());
        service.recordMilestones(jam);
        ModjamDiscordFeedService restarted = service(true, FAKE_DESTINATION);
        for (int i = 0; i < 3; i++) {
            restarted.reconcileMilestones();
            restarted.deliverPending();
        }
        assertEquals(4, outbox.events.size());
        assertTrue(outbox.events.values().stream().allMatch(event -> event.getState() == State.DELIVERED));
        verify(transport, times(4)).send(any(), any());
    }

    @Test
    void concurrentVotingSuppressesSeparateOpening() {
        jam.setStatus("ACTIVE");
        jam.setAllowConcurrentVoting(true);
        clock.advance(Duration.ofMinutes(4));
        service.reconcileMilestones();
        service.reconcileMilestones();
        assertEquals(List.of(Milestone.CREATED, Milestone.SUBMISSIONS_OPENED), outbox.milestones());
    }

    @Test
    void sameSubmissionAndVotingOpeningDoesNotCreateSeparateVoteEvent() {
        jam.setStatus("ACTIVE");
        jam.setEndDate(jam.getStartDate());
        clock.advance(Duration.ofMinutes(4));
        service.recordMilestones(jam);
        assertFalse(outbox.milestones().contains(Milestone.VOTING_OPENED));
    }

    @Test
    void disableFlagAndMissingOrInvalidDestinationMakeNoChangesOrTraffic() {
        jam.setStatus("COMPLETED");
        for (ModjamDiscordFeedService disabled : List.of(service(false, FAKE_DESTINATION),
                service(true, ""), service(true, "https://untrusted.test/hook"),
                service(true, "http://discord.com/api/webhooks/1/test-only-placeholder"))) {
            disabled.recordMilestones(jam);
            disabled.reconcileMilestones();
            disabled.deliverPending();
        }
        assertTrue(outbox.events.isEmpty());
        verifyNoInteractions(jams, transport);
    }

    @Test
    void failedSendIsRetriedWithoutResettingDeliveryStateOrRetryTime() {
        jam.setStatus("UPCOMING");
        when(transport.send(any(), any())).thenThrow(new IllegalStateException("simulated timeout"))
                .thenReturn(new DiscordFeedTransport.Result(200, Duration.ZERO));
        service.recordMilestones(jam);
        service.deliverPending();
        ModjamDiscordEvent event = outbox.events.values().iterator().next();
        assertEquals(State.PENDING, event.getState());
        Instant nextAttempt = event.getNextAttemptAt();
        service.recordMilestones(jam);
        assertEquals(nextAttempt, event.getNextAttemptAt());
        ModjamDiscordFeedService restarted = service(true, FAKE_DESTINATION);
        restarted.deliverPending();
        verify(transport, times(1)).send(any(), any());
        clock.advance(Duration.ofSeconds(30));
        restarted.deliverPending();
        assertEquals(State.DELIVERED, event.getState());
        restarted.deliverPending();
        verify(transport, times(2)).send(any(), any());
    }

    @Test
    void rateLimitBackoffIsSharedDurablyAcrossMilestonesAndRestarts() {
        jam.setStatus("ACTIVE");
        clock.advance(Duration.ofSeconds(60));
        when(transport.send(any(), any())).thenReturn(new DiscordFeedTransport.Result(429, Duration.ofSeconds(90)))
                .thenReturn(new DiscordFeedTransport.Result(200, Duration.ZERO));
        service.recordMilestones(jam);
        service.deliverPending();
        service(true, FAKE_DESTINATION).deliverPending();
        clock.advance(Duration.ofSeconds(89));
        service.deliverPending();
        verify(transport, times(1)).send(any(), any());
        clock.advance(Duration.ofSeconds(1));
        service(true, FAKE_DESTINATION).deliverPending();
        verify(transport, times(3)).send(any(), any());
        assertTrue(outbox.events.values().stream().allMatch(event -> event.getState() == State.DELIVERED));
    }

    @Test
    void abandonedLeaseRecoversAfterRestartAndOldLeaseCannotAcknowledgeNewWork() {
        jam.setStatus("UPCOMING");
        service.recordMilestones(jam);
        ModjamDiscordEvent abandoned = outbox.claimNext(clock.instant(), Duration.ofMinutes(2)).orElseThrow();
        service(true, FAKE_DESTINATION).deliverPending();
        verifyNoInteractions(transport);
        clock.advance(Duration.ofMinutes(2));
        ModjamDiscordEvent reclaimed = outbox.claimNext(clock.instant(), Duration.ofMinutes(2)).orElseThrow();
        outbox.delivered(abandoned, clock.instant());
        assertEquals(State.PENDING, outbox.events.get(abandoned.getId()).getState());
        outbox.retry(reclaimed, clock.instant(), null);
        service(true, FAKE_DESTINATION).deliverPending();
        verify(transport, times(1)).send(any(), any());
    }

    @Test
    void concurrentWorkersDoNotSendTheSameMilestone() throws Exception {
        jam.setStatus("UPCOMING");
        service.recordMilestones(jam);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger sends = new AtomicInteger();
        DiscordFeedTransport blockedTransport = (uri, body) -> {
            sends.incrementAndGet();
            sending.countDown();
            try { assertTrue(finish.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException exception) { throw new IllegalStateException(exception); }
            return new DiscordFeedTransport.Result(200, Duration.ZERO);
        };
        ModjamDiscordFeedService worker = new ModjamDiscordFeedService(jams, outbox, blockedTransport,
                new AppModjamDiscordFeedProperties(true, FAKE_DESTINATION),
                new AppFrontendProperties("https://modtale.test"), clock);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(worker::deliverPending);
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            threads.submit(worker::deliverPending).get(5, TimeUnit.SECONDS);
            finish.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally { finish.countDown(); }
        assertEquals(1, sends.get());
    }

    @Test
    void queuedPublicEventIsWithheldIfJamIsUnpublishedAndUsesCurrentPublicDetails() {
        jam.setStatus("UPCOMING");
        service.recordMilestones(jam);
        jam.setStatus("DRAFT");
        jam.setTitle("Private draft title");
        service.deliverPending();
        verifyNoInteractions(transport);
        jam.setStatus("UPCOMING");
        jam.setTitle("Published @everyone <@123> `title`");
        clock.advance(Duration.ofMinutes(1));
        service.deliverPending();
        var body = org.mockito.ArgumentCaptor.forClass(Map.class);
        var destination = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(transport).send(destination.capture(), body.capture());
        assertEquals("wait=true", destination.getValue().getQuery());
        assertEquals(Map.of("parse", List.of(), "users", List.of(), "roles", List.of(), "replied_user", false),
                body.getValue().get("allowed_mentions"));
        String content = (String) body.getValue().get("content");
        assertFalse(content.contains("Private draft title"));
        assertFalse(content.contains("@everyone"));
        assertTrue(content.contains("https://modtale.test/jam/test-jam"));
    }

    @Test
    void deletionDiscardsPendingEventWithoutSending() {
        jam.setStatus("UPCOMING");
        service.recordMilestones(jam);
        when(jams.findById(jam.getId())).thenReturn(Optional.empty());
        service.deliverPending();
        assertEquals(State.DISCARDED, outbox.events.values().iterator().next().getState());
        verifyNoInteractions(transport);
    }

    @Test
    void enqueueFailureDoesNotBreakLifecycleAndScheduledReconciliationRepairsIt() {
        jam.setStatus("UPCOMING");
        ModjamDiscordOutboxStore failed = mock(ModjamDiscordOutboxStore.class);
        when(failed.activationTime(any())).thenReturn(clock.instant());
        doThrow(new IllegalStateException("DB temporarily unavailable")).when(failed).enqueue(any());
        var failureTolerant = new ModjamDiscordFeedService(jams, failed, transport,
                new AppModjamDiscordFeedProperties(true, FAKE_DESTINATION),
                new AppFrontendProperties("https://modtale.test"), clock);
        assertDoesNotThrow(() -> failureTolerant.recordMilestones(jam));
        assertTrue(outbox.events.isEmpty());
        service.reconcileMilestones();
        assertEquals(1, outbox.events.size());
    }

    @Test
    void firstEnableSkipsHistoricalJamsAndOldDraftCreationDoesNotHideNewPublication() {
        jam.setStatus("COMPLETED");
        jam.setPublishedAt(null);
        jam.setStartDate(clock.instant().minusSeconds(180));
        jam.setEndDate(clock.instant().minusSeconds(120));
        jam.setVotingEndDate(clock.instant().minusSeconds(60));
        jam.setUpdatedAt(clock.instant().minusSeconds(30));
        jam.setWinnersAnnouncedAt(clock.instant().minusSeconds(30));
        service.reconcileMilestones();
        service.deliverPending();
        assertTrue(outbox.events.isEmpty());
        verifyNoInteractions(transport);

        jam.setStatus("UPCOMING");
        jam.setPublishedAt(clock.instant());
        jam.setStartDate(clock.instant().plusSeconds(60));
        jam.setEndDate(clock.instant().plusSeconds(120));
        jam.setVotingEndDate(clock.instant().plusSeconds(180));
        service.recordMilestones(jam);
        assertEquals(List.of(Milestone.CREATED), outbox.milestones());
    }

    @Test
    void persistedActivationRepairsMilestonesThatOccurredDuringDowntime() {
        jam.setStatus("ACTIVE");
        clock.advance(Duration.ofMinutes(3));
        var restarted = service(true, FAKE_DESTINATION);
        restarted.reconcileMilestones();
        restarted.deliverPending();
        assertEquals(3, outbox.events.size());
        verify(transport, times(3)).send(any(), any());
    }

    @Test
    void editingOldCompletedJamDoesNotInventFreshWinnerAnnouncement() {
        jam.setStatus("COMPLETED");
        jam.setPublishedAt(clock.instant().minusSeconds(3600));
        jam.setStartDate(clock.instant().minusSeconds(180));
        jam.setEndDate(clock.instant().minusSeconds(120));
        jam.setVotingEndDate(clock.instant().minusSeconds(60));
        jam.setWinnersAnnouncedAt(clock.instant().minusSeconds(30));
        jam.setUpdatedAt(clock.instant());
        service.recordMilestones(jam);
        service.reconcileMilestones();
        assertTrue(outbox.events.isEmpty());
        jam.setWinnersAnnouncedAt(null); // Legacy completed records have no server announcement timestamp.
        service.reconcileMilestones();
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void temporaryInitialActivationFailureDoesNotMoveCutoffPastNewPublication() {
        Instant requestedAt = clock.instant();
        AtomicInteger attempts = new AtomicInteger();
        MemoryOutbox temporarilyUnavailable = new MemoryOutbox() {
            @Override public synchronized Instant activationTime(Instant requested) {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("temporary activation failure");
                assertEquals(requestedAt, requested);
                return super.activationTime(requested);
            }
        };
        var recovering = new ModjamDiscordFeedService(jams, temporarilyUnavailable, transport,
                new AppModjamDiscordFeedProperties(true, FAKE_DESTINATION),
                new AppFrontendProperties("https://modtale.test"), clock);
        jam.setStatus("UPCOMING");
        clock.advance(Duration.ofMinutes(1));
        recovering.recordMilestones(jam);
        assertEquals(List.of(Milestone.CREATED, Milestone.SUBMISSIONS_OPENED), temporarilyUnavailable.milestones());
    }

    private ModjamDiscordFeedService service(boolean enabled, String destination) {
        return new ModjamDiscordFeedService(jams, outbox, transport,
                new AppModjamDiscordFeedProperties(enabled, destination),
                new AppFrontendProperties("https://modtale.test"), clock);
    }

    private static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Persisted fake state is deliberately shared by independently constructed service instances. */
    private static class MemoryOutbox implements ModjamDiscordOutboxStore {
        final Map<String, ModjamDiscordEvent> events = new LinkedHashMap<>();
        private Instant blockedUntil;
        private Instant activatedAt;
        @Override public synchronized void enqueue(ModjamDiscordEvent event) { events.putIfAbsent(event.getId(), event); }
        List<Milestone> milestones() { return events.values().stream().map(ModjamDiscordEvent::getMilestone).toList(); }
        @Override public synchronized Optional<ModjamDiscordEvent> claimNext(Instant now, Duration lease) {
            if (blockedUntil != null && now.isBefore(blockedUntil)) return Optional.empty();
            Optional<ModjamDiscordEvent> next = events.values().stream()
                    .filter(event -> event.getState() == State.PENDING && !now.isBefore(event.getNextAttemptAt()))
                    .filter(event -> event.getLeaseUntil() == null || !now.isBefore(event.getLeaseUntil()))
                    .min(Comparator.comparing(ModjamDiscordEvent::getOccurredAt).thenComparing(ModjamDiscordEvent::getId));
            return next.map(event -> {
                event.setLeaseToken(UUID.randomUUID().toString());
                event.setLeaseUntil(now.plus(lease));
                event.setAttempts(event.getAttempts() + 1);
                ModjamDiscordEvent snapshot = new ModjamDiscordEvent();
                BeanUtils.copyProperties(event, snapshot);
                return snapshot;
            });
        }
        private ModjamDiscordEvent owned(ModjamDiscordEvent event) {
            ModjamDiscordEvent current = events.get(event.getId());
            return current != null && current.getState() == State.PENDING
                    && java.util.Objects.equals(event.getLeaseToken(), current.getLeaseToken()) ? current : null;
        }
        private void clearLease(ModjamDiscordEvent current) { current.setLeaseToken(null); current.setLeaseUntil(null); }
        @Override public synchronized void delivered(ModjamDiscordEvent event, Instant now) {
            ModjamDiscordEvent current = owned(event);
            if (current != null) { current.setState(State.DELIVERED); current.setDeliveredAt(now); clearLease(current); }
        }
        @Override public synchronized void retry(ModjamDiscordEvent event, Instant nextAttempt, Integer status) {
            ModjamDiscordEvent current = owned(event);
            if (current != null) { current.setNextAttemptAt(nextAttempt); current.setLastStatus(status); clearLease(current); }
        }
        @Override public synchronized void discard(ModjamDiscordEvent event) {
            ModjamDiscordEvent current = owned(event);
            if (current != null) { current.setState(State.DISCARDED); clearLease(current); }
        }
        @Override public synchronized void pauseUntil(Instant until) {
            if (blockedUntil == null || blockedUntil.isBefore(until)) blockedUntil = until;
        }
        @Override public synchronized Instant activationTime(Instant now) {
            if (activatedAt == null) activatedAt = now;
            return activatedAt;
        }
    }
}
