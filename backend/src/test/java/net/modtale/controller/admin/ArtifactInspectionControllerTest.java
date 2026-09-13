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
        var diff=ArtifactInspectionController.compare("snapshot","1.0",true,true,before,after);
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
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.structure("project","1.0",net.modtale.service.admin.review.ProjectReviewSnapshot.token(project))).getStatusCode().value());
        verifyNoInteractions(inspector);
    }
    private static final class Fixture {
        final ProjectService projects=mock(ProjectService.class);
        final StorageService storage=mock(StorageService.class);
        final WardenClientService inspector=mock(WardenClientService.class);
        final Project project=new Project();
        final ProjectVersion before=new ProjectVersion(), after=new ProjectVersion();
        final byte[] bytes="fixture artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final ArtifactInspectionController controller=new ArtifactInspectionController(projects,storage,inspector);
        final WardenClientService.InspectionResponse response;
        Fixture() throws Exception {
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            before.setId("before");before.setVersionNumber("1");before.setHash(hash);before.setFileUrl("before.zip");
            before.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);before.setSecurityApprovedAt(1);
            before.setApprovedSecurityContextSha256(net.modtale.service.security.scan.ArtifactReviewContext.fingerprint(before));
            after.setId("after");after.setVersionNumber("2");after.setHash(hash);after.setFileUrl("after.zip");
            project.setId("project");project.setVersions(List.of(before,after));
            response=new WardenClientService.InspectionResponse(hash,List.of("file.json"),"{}","TEXT",Map.of("file.json","a".repeat(64)),"policy");
            when(projects.getRawProjectById("project")).thenReturn(project);
            when(storage.download(anyString())).thenReturn(bytes);
            when(inspector.inspectFile(any(),anyString(),nullable(String.class))).thenReturn(response);
        }
    }
    @Test void comparisonRejectsReviewAndContextChangesDuringInspection() throws Exception {
        for (int scenario=0;scenario<4;scenario++) {
            var f=new Fixture();String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);final int change=scenario;
            doAnswer(invocation->{
                switch(change) {
                    case 0 -> f.before.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
                    case 1 -> f.before.setFindingReviewHead("new-head");
                    case 2 -> f.after.setGameVersions(List.of("changed-runtime"));
                    case 3 -> f.project.setVersions(List.of(f.after));
                }
                return f.response;
            }).when(f.inspector).inspectFile(any(),anyString(),nullable(String.class));
            assertEquals(409,assertThrows(ResponseStatusException.class,()->f.controller.changes("project","2",token)).getStatusCode().value());
        }
    }
    @Test void fileAndStructureRejectChangesWhileArtifactIsInspected() throws Exception {
        for(boolean content:new boolean[]{false,true}) {
            var f=new Fixture();String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
            doAnswer(invocation->{f.after.setFindingReviewHead("new-review");return f.response;})
                    .when(f.inspector).inspectFile(any(),anyString(),nullable(String.class));
            assertEquals(409,assertThrows(ResponseStatusException.class,()->{
                if(content)f.controller.file("project","2","file.json",token);else f.controller.structure("project","2",token);
            }).getStatusCode().value());
        }
    }
    @Test void ambiguousVersionNamesNeverSelectAnArbitraryArtifact() throws Exception {
        var f=new Fixture();f.before.setVersionNumber("2");String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->f.controller.changes("project","2",token)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->f.controller.structure("project","2",token)).getStatusCode().value());
        verifyNoInteractions(f.storage,f.inspector);
    }
    @Test void unchangedInspectionUsesOneCapturedSelectionAndIgnoresDownloadCounters() throws Exception {
        var f=new Fixture();String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
        doAnswer(invocation->{f.after.setDownloadCount(2);return f.response;})
                .when(f.inspector).inspectFile(any(),anyString(),nullable(String.class));
        var result=f.controller.changes("project","2",token).getBody();
        assertEquals("1",result.baselineVersion());assertEquals(1,result.unchanged());assertTrue(result.contextComparable());
        verify(f.projects,times(2)).getRawProjectById("project");
        verify(f.storage).download("before.zip");verify(f.storage).download("after.zip");
    }
    @Test void editedBaselineContextCannotBePresentedAsComparableApprovedContext() throws Exception {
        var f=new Fixture();f.before.setGameVersions(List.of("edited-since-approval"));String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
        assertFalse(f.controller.changes("project","2",token).getBody().contextComparable());
    }

    @Test void comparisonTokenBindsLaterFileAndStructureReadsBeforeAnyDownload() throws Exception {
        var f=new Fixture();String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
        assertEquals(token,f.controller.changes("project","2",token).getBody().reviewToken());
        clearInvocations(f.storage,f.inspector);
        f.before.setFindingReviewHead("revoked-after-comparison");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->f.controller.file("project","1","file.json",token)).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->f.controller.structure("project","2",token)).getStatusCode().value());
        verifyNoInteractions(f.storage,f.inspector);
    }
    @Test void httpInspectionRequiresTheOpenedProjectTokenOnEveryRoute() throws Exception {
        var f=new Fixture();String token=net.modtale.service.admin.review.ProjectReviewSnapshot.token(f.project);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(f.controller).build();
        for(String route:List.of("changes","structure","file")) {
            var path="/api/v1/admin/projects/project/versions/2/"+route;
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("path","file.json"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("path","file.json").header("If-Match","stale"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
            verifyNoInteractions(f.storage,f.inspector);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).param("path","file.json").header("If-Match",token))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
            clearInvocations(f.storage,f.inspector);
        }
    }
}
