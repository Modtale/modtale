package net.modtale.service.jam;

import java.util.List;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModjamProjectReleasePersistenceTest {
    @Test
    void claimRequiresUncanceledPrivateSnapshotAndCurrentSafeVersions() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ModjamProjectReleasePersistence persistence = new ModjamProjectReleasePersistence(mongo);
        Project snapshot = snapshot();
        Project claimed = new Project();
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Project.class)))
                .thenReturn(claimed);
        assertSame(claimed, persistence.claimRelease(snapshot));
        var query = ArgumentCaptor.forClass(Query.class);
        var change = ArgumentCaptor.forClass(Update.class);
        var options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), change.capture(), options.capture(), eq(Project.class));
        var clauses = query.getValue().getQueryObject().getList("$and", Document.class);
        var guard = clauses.getFirst();
        assertEquals("project", guard.getString("_id"));
        assertEquals(ProjectStatus.PRIVATE, guard.get("status"));
        assertEquals(true, guard.getBoolean("modjamPublicationPending"));
        assertTrue(guard.containsKey("deletedAt"));
        assertNull(guard.get("deletedAt"));
        assertEquals("observed-update", guard.getString("updatedAt"));
        assertEquals(List.of("jam"), guard.get("modjamIds"));
        assertEquals(ProjectClassification.PLUGIN, guard.get("classification"));
        assertTrue(clauses.get(1).toString().contains("SCANNING"));
        assertTrue(clauses.get(2).toString().contains("APPROVED"));
        var fields = change.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals(Set.of("status", "modjamPublicationPending", "updatedAt", "rankingDirty"), fields.keySet());
        assertEquals(ProjectStatus.PUBLISHED, fields.get("status"));
        assertEquals(false, fields.getBoolean("modjamPublicationPending"));
        assertEquals(true, fields.getBoolean("rankingDirty"));
        assertFalse(options.getValue().isUpsert());
        assertTrue(options.getValue().isReturnNew());
        assertEquals(ProjectStatus.PRIVATE, snapshot.getStatus());
        assertTrue(snapshot.isModjamPublicationPending());
        verify(mongo, never()).save(any());
    }

    @Test
    void unmatchedCanceledClaimDoesNotMutateSnapshotOrInsertAProject() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Project snapshot = snapshot();
        assertNull(new ModjamProjectReleasePersistence(mongo).claimRelease(snapshot));
        assertEquals(ProjectStatus.PRIVATE, snapshot.getStatus());
        assertTrue(snapshot.isModjamPublicationPending());
        verify(mongo, never()).save(any());
        verify(mongo, never()).insert(any());
    }

    private static Project snapshot() {
        Project project = new Project();
        project.setId("project");
        project.setStatus(ProjectStatus.PRIVATE);
        project.setModjamPublicationPending(true);
        project.setUpdatedAt("observed-update");
        project.setClassification(ProjectClassification.PLUGIN);
        project.setModjamIds(List.of("jam"));
        return project;
    }
}
