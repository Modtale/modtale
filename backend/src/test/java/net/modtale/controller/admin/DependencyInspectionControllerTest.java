package net.modtale.controller.admin;

import net.modtale.model.project.*;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static net.modtale.service.security.scan.DependencyReviewGraph.*;

class DependencyInspectionControllerTest {
    final ProjectService projects=mock(ProjectService.class);
    final DependencyReviewSource source=mock(DependencyReviewSource.class);
    final Supplier<DependencyReviewSource> sources=mock(Supplier.class);
    final Project project=new Project();final ProjectVersion version=new ProjectVersion();
    final DependencyInspectionController controller=new DependencyInspectionController(projects,sources);
    DependencyInspectionControllerTest() {
        project.setId("p");version.setId("v");version.setVersionNumber("1");version.setHash("a".repeat(64));version.setFileUrl("files/v");
        project.setVersions(List.of(version));when(projects.getRawProjectById("p")).thenReturn(project);when(sources.get()).thenReturn(source);
        when(source.readRoot("p","v")).thenAnswer(i->new Lookup(State.FOUND,snapshot()));
        when(source.read(any())).thenAnswer(i->new Lookup(State.FOUND,snapshot()));
    }
    Snapshot snapshot(){return new Snapshot("p","v","1","files/v","a".repeat(64),ArtifactReviewContext.fingerprint(version),null,false,List.of());}
    @Test void missingAndStaleReviewTokensStopBeforeGraphAccess() {
        for(String token:Arrays.asList(null,"stale"))assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.inspect("p","v",token)).getStatusCode().value());
        verifyNoInteractions(sources,source);
    }
    @Test void exactVersionIdsAndRootBindingPreventSelectingAnotherUpload() {
        String token=ProjectReviewSnapshot.token(project);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.inspect("p","1",token)).getStatusCode().value());
        when(source.readRoot("p","v")).thenReturn(new Lookup(State.FOUND,new Snapshot("p","v","1","files/v","b".repeat(64),ArtifactReviewContext.fingerprint(version),null,false,List.of())));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.inspect("p","v",token)).getStatusCode().value());
        verify(source,never()).read(any());
    }
    @Test void ambiguousAndUnavailableRootsDoNotProduceAnInventory() {
        for(var state:List.of(State.AMBIGUOUS,State.MISSING,State.UNAVAILABLE)) {
            when(source.readRoot("p","v")).thenReturn(new Lookup(state,null));
            assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.inspect("p","v",ProjectReviewSnapshot.token(project))).getStatusCode().value());
        }
    }
    @Test void rootReviewMutationDuringGraphTraversalRejectsTheResponse() {
        String token=ProjectReviewSnapshot.token(project);
        when(source.read(any())).thenAnswer(i->{version.setFindingReviewHead("changed");return new Lookup(State.FOUND,snapshot());});
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.inspect("p","v",token)).getStatusCode().value());
    }
    @Test void dependencyReadFailureIsAnExplicitGapNotClearance() {
        when(source.read(any())).thenReturn(new Lookup(State.UNAVAILABLE,null));
        var response=controller.inspect("p","v",ProjectReviewSnapshot.token(project)).getBody();
        assertFalse(response.artifactBytesVerified());assertNull(response.inventory().identity());assertFalse(response.inventory().gaps().isEmpty());
    }
    @Test void httpRouteRequiresSnapshotAndReturnsUncachedDeclarationInventory() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var route="/api/v1/admin/projects/p/version-ids/v/dependencies";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        verifyNoInteractions(sources,source);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header("If-Match",ProjectReviewSnapshot.token(project)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.artifactBytesVerified").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.inventory.root.versionId").value("v"));
    }
}
