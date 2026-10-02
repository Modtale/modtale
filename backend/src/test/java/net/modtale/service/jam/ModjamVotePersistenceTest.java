package net.modtale.service.jam;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.model.jam.ModjamSubmission;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ModjamVotePersistenceTest {
    private MongoTemplate mongo;
    private ModjamVotePersistence persistence;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        persistence = new ModjamVotePersistence(mongo);
    }

    @Test
    void replacesOnlyMatchingVoterAndCategoryInOneCurrentDocumentPipeline() {
        var vote = new ModjamSubmission.Vote("vote-1", "$voter", "$category", 7, true);
        persistence.replaceBallot("jam-1", "sub-1", vote);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(AggregationUpdate.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), options.capture(), eq(ModjamSubmission.class));
        assertEquals(new Document("_id", "sub-1").append("jamId", "jam-1"), query.getValue().getQueryObject());
        assertFalse(options.getValue().isUpsert());
        assertTrue(options.getValue().isReturnNew());
        List<Document> pipeline = update.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        assertEquals(1, pipeline.size());
        var fields = pipeline.getFirst().get("$set", Document.class);
        assertEquals(Set.of("votes", "voteRevision"), fields.keySet());
        var concat = fields.get("votes", Document.class).getList("$concatArrays", Document.class);
        var filter = concat.getFirst().get("$filter", Document.class);
        assertEquals(new Document("$ifNull", List.of("$votes", List.of())), filter.get("input"));
        var and = filter.get("cond", Document.class).getList("$not", Document.class).getFirst().getList("$and", Document.class);
        assertEquals(new Document("$eq", List.of("$$ballot.voterId", new Document("$literal", "$voter"))), and.getFirst());
        assertEquals(new Document("$eq", List.of("$$ballot.categoryId", new Document("$literal", "$category"))), and.getLast());
        var appended = concat.getLast().getList("$literal", Document.class).getFirst();
        assertEquals("vote-1", appended.getString("_id"));
        assertEquals(7, appended.getInteger("score"));
        assertEquals(true, appended.getBoolean("isJudge"));
        assertEquals(new Document("$add", List.of(new Document("$ifNull", List.of("$voteRevision", 0)), 1)), fields.get("voteRevision"));
    }

    @Test
    void noMatchNeverFallsBackToInsertOrWholeDocumentReplacement() {
        var vote = new ModjamSubmission.Vote("vote-1", "voter", "category", 7, false);
        assertNull(persistence.replaceBallot("jam-1", "missing", vote));
        verify(mongo, never()).save(any());
        verify(mongo, never()).insert(any());
    }

    @Test
    void scoreWritesAreNarrowAndGuardedByObservedVoteRevision() {
        ModjamSubmission snapshot = snapshot(12);
        persistence.saveScores(snapshot);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(ModjamSubmission.class));
        var clauses = query.getValue().getQueryObject().getList("$and", Document.class);
        assertEquals(new Document("_id", "sub-1").append("jamId", "jam-1"), clauses.getFirst());
        assertEquals(new Document("voteRevision", 12L), clauses.getLast());
        var fields = update.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals(Set.of("categoryScores", "totalScore", "judgeCategoryScores", "totalJudgeScore", "totalPublicScore", "rank"), fields.keySet());
        assertFalse(fields.containsKey("votes"));
        assertFalse(fields.containsKey("awardTitle"));
        verify(mongo, never()).save(any());
    }

    @Test
    void legacyUnversionedBallotsHaveAZeroOrMissingRevisionGuard() {
        persistence.saveScores(snapshot(0));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(query.capture(), any(Update.class), eq(ModjamSubmission.class));
        var revision = query.getValue().getQueryObject().getList("$and", Document.class).getLast();
        assertEquals(List.of(new Document("voteRevision", 0),
                new Document("voteRevision", new Document("$exists", false))), revision.get("$or"));
    }

    @Test
    void finalAwardsCannotOverwriteAnInFlightBallotOrScoreUpdate() {
        ModjamSubmission snapshot = snapshot(3);
        snapshot.setWinner(true);
        snapshot.setAwardTitle("Best Overall");
        persistence.saveAward(snapshot);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(ModjamSubmission.class));
        assertEquals(new Document("$set", new Document("isWinner", true).append("awardTitle", "Best Overall")),
                update.getValue().getUpdateObject());
        verify(mongo, never()).save(any());
    }

    private static ModjamSubmission snapshot(long revision) {
        ModjamSubmission snapshot = new ModjamSubmission();
        snapshot.setId("sub-1");
        snapshot.setJamId("jam-1");
        snapshot.setVoteRevision(revision);
        snapshot.setCategoryScores(Map.of("quality", 7.0));
        snapshot.setTotalScore(7.0);
        snapshot.setRank(1);
        return snapshot;
    }
}
