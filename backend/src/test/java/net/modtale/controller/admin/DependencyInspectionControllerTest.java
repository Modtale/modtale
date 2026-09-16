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
    final DependencyArtifactVerifier verifier=mock(DependencyArtifactVerifier.class);
    final DependencyInspectionController controller=new DependencyInspectionController(projects,sources,verifier);
    DependencyInspectionControllerTest() {
        project.setId("p");version.setId("v");version.setVersionNumber("1");version.setHash("a".repeat(64));version.setFileUrl("files/v");
        project.setVersions(List.of(version));when(projects.getRawProjectById("p")).thenReturn(project);when(sources.get()).thenReturn(source);
        when(source.readRoot("p","v")).thenAnswer(i->new Lookup(State.FOUND,snapshot()));
        when(source.read(any())).thenAnswer(i->new Lookup(State.FOUND,snapshot()));
    }
    Snapshot snapshot(){return new Snapshot("p","v","1","files/v","a".repeat(64),ArtifactReviewContext.fingerprint(version),null,false,List.of());}
    @Test void byteHttpRouteRequiresInventoryAndReturnsAnUncachedObservation() throws Exception {
        String token=ProjectReviewSnapshot.token(project);String identity=controller.inspect("p","v",token).getBody().inventory().identity();
        when(verifier.verify(any())).thenReturn(new DependencyArtifactVerifier.Result(identity,List.of(),DependencyArtifactVerifier.State.BUSY,0));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        String route="/api/v1/admin/projects/p/version-ids/v/dependency-bytes";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header("If-Match",token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verifyNoInteractions(verifier);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(route).header("If-Match",token).param("inventoryIdentity",identity))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.verification.state").value("BUSY"));
    }
    @Test void byteRequestsRequireBothTheOpenedReviewAndInventoryIdentityBeforeStorage() {
        String token=ProjectReviewSnapshot.token(project);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->controller.verifyBytes("p","v","invalid",token)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.verifyBytes("p","v","a".repeat(64),null)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.verifyBytes("p","v","a".repeat(64),token)).getStatusCode().value());
        verifyNoInteractions(verifier);
    }
    @Test void byteOutcomesRemainExplicitAndAreRevalidatedWithoutGrantingApproval() {
        String token=ProjectReviewSnapshot.token(project);String identity=controller.inspect("p","v",token).getBody().inventory().identity();
        for(var state:DependencyArtifactVerifier.State.values()) {
            when(verifier.verify(any())).thenReturn(new DependencyArtifactVerifier.Result(identity,List.of(),state,0));
            var result=controller.verifyBytes("p","v",identity,token);
            assertEquals("no-store",result.getHeaders().getCacheControl());assertEquals(state,result.getBody().verification().state());
            assertEquals(token,result.getBody().reviewToken());
        }
    }
    @Test void changedTransitiveArtifactAfterStorageDiscardsMatchedObservation() {
        var dependency=new ProjectDependency("child",null,"1");version.setDependencies(List.of(dependency));
        var root=new Snapshot("p","v","1","files/v","a".repeat(64),ArtifactReviewContext.fingerprint(version),null,false,
                List.of(new Dependency(dependency.getSource(),dependency.getDependencyType(),new Reference("child","1"))));
        var child=new java.util.concurrent.atomic.AtomicReference<>(new Snapshot("child","child-v","1","files/child","b".repeat(64),"c".repeat(64),null,false,List.of()));
        when(source.readRoot("p","v")).thenReturn(new Lookup(State.FOUND,root));
        when(source.read(any())).thenAnswer(i->new Lookup(State.FOUND,((Reference)i.getArgument(0)).projectId().equals("p")?root:child.get()));
        String token=ProjectReviewSnapshot.token(project);String identity=controller.inspect("p","v",token).getBody().inventory().identity();
        when(verifier.verify(any())).thenAnswer(i->{
            child.set(new Snapshot("child","child-v","1","files/child","d".repeat(64),"c".repeat(64),null,false,List.of()));
            return new DependencyArtifactVerifier.Result(identity,List.of(),DependencyArtifactVerifier.State.MATCHED,1);
        });
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.verifyBytes("p","v",identity,token)).getStatusCode().value());
    }
    @Test void rootReviewChangeDuringStorageDiscardsByteResult() {
        String token=ProjectReviewSnapshot.token(project);String identity=controller.inspect("p","v",token).getBody().inventory().identity();
        when(verifier.verify(any())).thenAnswer(i->{version.setFindingReviewHead("changed");return new DependencyArtifactVerifier.Result(identity,List.of(),DependencyArtifactVerifier.State.MATCHED,1);});
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.verifyBytes("p","v",identity,token)).getStatusCode().value());
    }
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
