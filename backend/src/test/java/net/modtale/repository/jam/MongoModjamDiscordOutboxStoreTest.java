package net.modtale.repository.jam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import net.modtale.model.jam.ModjamDiscordEvent;
import net.modtale.model.jam.ModjamDiscordEvent.Milestone;
import net.modtale.model.jam.ModjamDiscordFeedBackoff;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoModjamDiscordOutboxStoreTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoModjamDiscordOutboxStore store = new MongoModjamDiscordOutboxStore(mongo);
    private final Instant now = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void duplicateIdInsertDoesNotOverwriteDeliveredOrPendingRecord() {
        var event = event();
        when(mongo.insert(event)).thenThrow(new DuplicateKeyException("duplicate"));
        assertDoesNotThrow(() -> store.enqueue(event));
        verify(mongo).insert(event);
        verify(mongo, never()).save(any());
        assertEquals(event.getId(), event().getId());
    }

    @Test
    void atomicClaimReturnsNewLeaseAndRequiresDueUnleasedPendingState() {
        store.claimNext(now, Duration.ofMinutes(2));
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), options.capture(), eq(ModjamDiscordEvent.class));
        String filter = query.getValue().getQueryObject().toString();
        assertTrue(filter.contains("state"));
        assertTrue(filter.contains("PENDING"));
        assertTrue(filter.contains("nextAttemptAt"));
        assertTrue(filter.contains("leaseUntil"));
        assertTrue(options.getValue().isReturnNew());
        var changes = update.getValue().getUpdateObject().get("$set", org.bson.Document.class);
        assertEquals(now.plusSeconds(120), changes.get("leaseUntil"));
        assertNotNull(changes.get("leaseToken"));
        assertEquals(1, update.getValue().getUpdateObject().get("$inc", org.bson.Document.class).get("attempts"));
    }

    @Test
    void sharedRateLimitPausePreventsClaimAfterProcessRestart() {
        when(mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class))
                .thenReturn(new ModjamDiscordFeedBackoff("modjam-feed", now.plusSeconds(90), now));
        assertTrue(new MongoModjamDiscordOutboxStore(mongo).claimNext(now, Duration.ofMinutes(2)).isEmpty());
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ModjamDiscordEvent.class));
    }

    @Test
    void deliveryAcknowledgementRequiresOriginalLeaseToken() {
        var event = event();
        event.setLeaseToken("claim-1");
        store.delivered(event, now);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(ModjamDiscordEvent.class));
        assertEquals(event.getId(), query.getValue().getQueryObject().get("_id"));
        assertEquals("claim-1", query.getValue().getQueryObject().get("leaseToken"));
        var changes = update.getValue().getUpdateObject().get("$set", org.bson.Document.class);
        assertEquals(now, changes.get("deliveredAt"));
        assertEquals(ModjamDiscordEvent.State.DELIVERED, changes.get("state"));
    }

    @Test
    void activationUsesAtomicUniqueIdUpsertAndSurvivesRestarts() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ModjamDiscordFeedBackoff.class)))
                .thenReturn(new ModjamDiscordFeedBackoff("modjam-feed", null, now));
        assertEquals(now, store.activationTime(now));
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(any(Query.class), any(Update.class), options.capture(), eq(ModjamDiscordFeedBackoff.class));
        assertTrue(options.getValue().isUpsert());
        assertTrue(options.getValue().isReturnNew());
        when(mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class))
                .thenReturn(new ModjamDiscordFeedBackoff("modjam-feed", null, now));
        assertEquals(now, new MongoModjamDiscordOutboxStore(mongo).activationTime(now.plusSeconds(3600)));
    }

    @Test
    void concurrentActivationConflictReadsWinningTimestampInsteadOfResettingIt() {
        when(mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class)).thenReturn(null)
                .thenReturn(new ModjamDiscordFeedBackoff("modjam-feed", null, now.minusSeconds(1)));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ModjamDiscordFeedBackoff.class))).thenThrow(new DuplicateKeyException("concurrent activation"));
        assertEquals(now.minusSeconds(1), store.activationTime(now));
    }

    private ModjamDiscordEvent event() { return new ModjamDiscordEvent("jam-test", Milestone.CREATED, now, now); }
}
