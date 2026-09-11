package net.modtale.controller.admin;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.WardenClientService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtifactInspectionControllerTest {
    @Test void comparesFullCaseSensitiveNestedPathsAndRemovals() {
        var before=Map.of("nested.jar!/A.class","a", "removed.class","b", "same.json","c");
        var after=Map.of("nested.jar!/A.class","changed", "nested.jar!/a.class","a", "same.json","c");
        var diff=ArtifactInspectionController.compare("1.0",true,true,before,after);
        assertEquals(1,diff.added()); assertEquals(1,diff.modified()); assertEquals(1,diff.removed()); assertEquals(1,diff.unchanged());
        assertTrue(diff.contextChanged());
        assertTrue(diff.files().contains(new ArtifactInspectionController.FileChange("nested.jar!/a.class","ADDED")));
    }
    @Test void refusesStoredArtifactMismatchBeforeRequestingInspection() {
        var projects=mock(ProjectService.class); var storage=mock(StorageService.class); var inspector=mock(WardenClientService.class);
        var project=new Project();var version=new ProjectVersion();version.setVersionNumber("1.0");version.setHash("a".repeat(64));
        version.setFileUrl("stored.zip");project.setVersions(List.of(version));
        when(projects.getRawProjectById("project")).thenReturn(project);
        when(storage.download("stored.zip")).thenReturn("different".getBytes());
        var controller=new ArtifactInspectionController(projects,storage,inspector);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.structure("project","1.0")).getStatusCode().value());
        verifyNoInteractions(inspector);
    }
}
