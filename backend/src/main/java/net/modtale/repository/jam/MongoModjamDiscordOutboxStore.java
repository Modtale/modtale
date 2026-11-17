package net.modtale.repository.jam;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.modtale.model.jam.ModjamDiscordEvent;
import net.modtale.model.jam.ModjamDiscordEvent.State;
import net.modtale.model.jam.ModjamDiscordFeedBackoff;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class MongoModjamDiscordOutboxStore implements ModjamDiscordOutboxStore {
    private final MongoTemplate mongo;

    public MongoModjamDiscordOutboxStore(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void enqueue(ModjamDiscordEvent event) {
        try {
            // Mongo's built-in unique _id index deduplicates across processes and restarts.
            mongo.insert(event);
        } catch (DuplicateKeyException ignored) {
            // Never overwrite a delivered event or reset a pending event's retries.
        }
    }

    @Override
    public Optional<ModjamDiscordEvent> claimNext(Instant now, Duration lease) {
        ModjamDiscordFeedBackoff backoff = mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class);
        if (backoff != null && backoff.blockedUntil() != null && now.isBefore(backoff.blockedUntil())) {
            return Optional.empty();
        }
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("state").is(State.PENDING),
                Criteria.where("nextAttemptAt").lte(now),
                new Criteria().orOperator(Criteria.where("leaseUntil").is(null),
                        Criteria.where("leaseUntil").lte(now)));
        Query query = new Query(criteria).with(Sort.by("occurredAt", "_id"));
        Update update = new Update().set("leaseUntil", now.plus(lease))
                .set("leaseToken", UUID.randomUUID().toString()).inc("attempts", 1);
        return Optional.ofNullable(mongo.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ModjamDiscordEvent.class));
    }

    @Override
    public void delivered(ModjamDiscordEvent event, Instant now) {
        mongo.updateFirst(owned(event), clearLease().set("state", State.DELIVERED)
                .set("deliveredAt", now).unset("lastStatus"), ModjamDiscordEvent.class);
    }

    @Override
    public void retry(ModjamDiscordEvent event, Instant nextAttempt, Integer status) {
        mongo.updateFirst(owned(event), clearLease().set("nextAttemptAt", nextAttempt)
                .set("lastStatus", status), ModjamDiscordEvent.class);
    }

    @Override
    public void discard(ModjamDiscordEvent event) {
        mongo.updateFirst(owned(event), clearLease().set("state", State.DISCARDED), ModjamDiscordEvent.class);
    }

    @Override
    public void pauseUntil(Instant until) {
        mongo.upsert(new Query(Criteria.where("_id").is("modjam-feed")),
                new Update().max("blockedUntil", until), ModjamDiscordFeedBackoff.class);
    }

    @Override
    public Instant activationTime(Instant now) {
        ModjamDiscordFeedBackoff state = mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class);
        if (state != null && state.activatedAt() != null) return state.activatedAt();
        try {
            // The conditional unique-ID upsert chooses one first activation across processes.
            state = mongo.findAndModify(new Query(Criteria.where("_id").is("modjam-feed")
                            .and("activatedAt").is(null)), new Update().set("activatedAt", now),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), ModjamDiscordFeedBackoff.class);
            if (state == null || state.activatedAt() == null) throw new IllegalStateException("Feed activation unavailable");
            return state.activatedAt();
        } catch (DuplicateKeyException concurrentActivation) {
            state = mongo.findById("modjam-feed", ModjamDiscordFeedBackoff.class);
            if (state == null || state.activatedAt() == null) throw concurrentActivation;
            return state.activatedAt();
        }
    }

    private Query owned(ModjamDiscordEvent event) {
        return new Query(Criteria.where("_id").is(event.getId())
                .and("leaseToken").is(event.getLeaseToken()).and("state").is(State.PENDING));
    }

    private Update clearLease() { return new Update().unset("leaseUntil").unset("leaseToken"); }
}
