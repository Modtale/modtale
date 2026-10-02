package net.modtale.service.jam;

import java.util.List;
import java.util.Set;
import net.modtale.model.jam.Modjam;
import net.modtale.model.user.User;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModjamMembershipPersistenceTest {
    private MongoTemplate mongo;
    private ModjamMembershipPersistence persistence;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        persistence = new ModjamMembershipPersistence(mongo);
    }

    @Test
    void joinsUseTargetedSetOperationsOnBothSidesAndRepeatRepairsInterruptedWrites() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Modjam.class)))
                .thenReturn(new Modjam());
        persistence.participate("jam", "user", true);
        persistence.participate("jam", "user", true);
        var change = ArgumentCaptor.forClass(Update.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo, times(2)).findAndModify(any(Query.class), change.capture(), options.capture(), eq(Modjam.class));
        assertEquals(new Document("$addToSet", new Document("participantIds", "user")), change.getValue().getUpdateObject());
        assertFalse(options.getValue().isUpsert());
        var accountChange = ArgumentCaptor.forClass(Update.class);
        var accountQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(4)).updateFirst(accountQuery.capture(), accountChange.capture(), eq(User.class));
        assertEquals(new Document("$addToSet", new Document("joinedModjamIds", "jam")), accountChange.getValue().getUpdateObject());
        assertEquals(new Document("_id", "user").append("deletedAt", null), accountQuery.getValue().getQueryObject());
        assertEquals(new Document("_id", "user").append("deletedAt", null).append("joinedModjamIds", null),
                accountQuery.getAllValues().getFirst().getQueryObject());
        verify(mongo, never()).save(any());
    }

    @Test
    void leavesPullOnlyTheRequestedRelationship() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Modjam.class)))
                .thenReturn(new Modjam());
        persistence.participate("jam", "user", false);
        var change = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), change.capture(), any(FindAndModifyOptions.class), eq(Modjam.class));
        assertEquals(new Document("$pull", new Document("participantIds", "user")), change.getValue().getUpdateObject());
        var accountChange = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(any(Query.class), accountChange.capture(), eq(User.class));
        assertEquals(new Document("$pull", new Document("joinedModjamIds", "jam")), accountChange.getValue().getUpdateObject());
    }

    @Test
    void missingJamDoesNotCreateDocumentsOrChangeTheAccount() {
        assertNull(persistence.participate("missing", "user", true));
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(User.class));
        verify(mongo, never()).save(any());
        verify(mongo, never()).insert(any());
    }

    @Test
    void invitationAddsOneIdentityToCurrentMapWithoutReplacingOtherInvitesOrRoles() {
        persistence.inviteJudge("jam", "$user.id", "$display");
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(AggregationUpdate.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), options.capture(), eq(Modjam.class));
        assertEquals("jam", query.getValue().getQueryObject().getString("_id"));
        assertTrue(query.getValue().getQueryObject().containsKey("$expr"));
        assertFalse(options.getValue().isUpsert());
        var pipeline = update.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        assertEquals(2, pipeline.size());
        var fields = pipeline.getFirst().get("$set", Document.class);
        assertEquals(Set.of("pendingJudgeInviteUsers"), fields.keySet());
        String json = fields.toJson();
        assertTrue(json.contains("$objectToArray"));
        assertTrue(json.contains("$arrayToObject"));
        assertTrue(json.contains("$literal"));
        assertTrue(json.contains("$user.id"));
        assertTrue(json.contains("$display"));
        var display = pipeline.getLast().get("$set", Document.class);
        assertEquals(Set.of("pendingJudgeInvites"), display.keySet());
        assertTrue(display.toJson().contains("$pendingJudgeInviteUsers"));
    }

    @Test
    void acceptanceRequiresAnIdBoundPendingInviteAndAddsJudgeAsASet() {
        persistence.answerJudgeInvite("jam", "user", true);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(AggregationUpdate.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Modjam.class));
        String guard = query.getValue().getQueryObject().get("$expr", Document.class).toJson();
        assertTrue(guard.contains("$in"));
        assertTrue(guard.contains("$literal"));
        assertTrue(guard.contains("$$invite.k"));
        var fields = update.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).getFirst().get("$set", Document.class);
        assertEquals(Set.of("pendingJudgeInviteUsers", "judgeIds"), fields.keySet());
        assertTrue(fields.get("pendingJudgeInviteUsers", Document.class).toJson().contains("$filter"));
        assertEquals(new Document("$setUnion", List.of(new Document("$ifNull", List.of("$judgeIds", List.of())),
                new Document("$literal", List.of("user")))), fields.get("judgeIds"));
    }

    @Test
    void declineDoesNotReplaceOrRemoveAcceptedJudges() {
        persistence.answerJudgeInvite("jam", "user", false);
        var update = ArgumentCaptor.forClass(AggregationUpdate.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Modjam.class));
        var fields = update.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).getFirst().get("$set", Document.class);
        assertEquals(Set.of("pendingJudgeInviteUsers"), fields.keySet());
    }

    @Test
    void removalsFilterOnlySelectedIdsFromCurrentRoster() {
        persistence.removeJudges("jam", List.of("user"));
        var update = ArgumentCaptor.forClass(AggregationUpdate.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Modjam.class));
        var fields = update.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).getFirst().get("$set", Document.class);
        assertEquals(Set.of("pendingJudgeInviteUsers", "judgeIds"), fields.keySet());
        assertEquals(new Document("$setDifference", List.of(new Document("$ifNull", List.of("$judgeIds", List.of())),
                new Document("$literal", List.of("user")))), fields.get("judgeIds"));
        assertFalse(fields.containsKey("participantIds"));
        assertFalse(fields.containsKey("organizerMembers"));
        verify(mongo, never()).save(any());
    }
}
