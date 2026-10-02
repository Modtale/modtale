package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import net.modtale.service.security.access.*;
import net.modtale.service.user.account.AccountService;
import net.modtale.repository.user.*;
import net.modtale.model.user.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationOwnerAccessTest {
    ProjectMutationExecutorTest base=new ProjectMutationExecutorTest();ProjectMutationOwnerAccess owner;ReviewRepairWorkflow budget;ApiKeyRepository keys;User user;
    @BeforeEach void setup()throws Exception{
        base.setup();var mongo=base.base.base.fixture.mongo;
        mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),new Document("$set",new Document("authorId","owner").append("status","PUBLISHED")));
        user=new User();user.setId("owner");var accounts=mock(AccountService.class);when(accounts.getCurrentUser(any())).thenReturn(user);
        var access=new AccessControlService(accounts,mock(UserRepository.class),mock(PermissionProjectLookupService.class),mongo);
        keys=new MongoRepositoryFactory(mongo).getRepository(ApiKeyRepository.class);
        var authority=new ProjectMutationOwnerAuthority(accounts,access,keys,mongo);var reader=new ProjectMutationReferenceReader(mongo,base.base.base.archive,base.base.service,base.executor());reader.initialize();
        budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1);
        owner=new ProjectMutationOwnerAccess(budget,authority,base.base.service,base.executor(),reader);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();if(budget!=null)budget.close();base.cleanup();}
    ProjectMutationPreparation.Prepared prepare(){var r=base.base.request();return owner.prepare(r.id(),r.projectId(),r.expectedSha256(),r.mutation(),r.proposedProject());}
    void api(Set<ApiKey.ApiPermission> scopes){
        String plain="md_owner-access-fixture";var key=new ApiKey("owner","fixture",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(plain),plain.substring(0,10));
        key.setId("key");key.setContextPermissions(Map.of("PERSONAL",scopes));keys.insert(key);
        var request=new MockHttpServletRequest();request.addHeader("X-MODTALE-KEY",plain);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_API"))));
    }
    @Test void actualOwnerBoundarySignsAndAppliesGroupWithServerAttributedActor(){
        var prepared=prepare();assertEquals("APPLIED",owner.apply(prepared).state());
        assertEquals("owner",base.base.base.archive.load(prepared.id()).actorId());assertEquals(0,budget.status().active());
    }
    @Test void replacementRequiresBothCreateAndDeleteScopes(){
        api(Set.of(ApiKey.ApiPermission.VERSION_CREATE));assertThrows(SecurityException.class,this::prepare);
        assertEquals(0,base.base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());
        assertEquals(0,budget.status().active());
    }
    @Test void revokedKeyBetweenPreparationAndApplicationCannotApply(){
        api(Set.of(ApiKey.ApiPermission.VERSION_CREATE,ApiKey.ApiPermission.VERSION_DELETE));var prepared=prepare();var before=base.root();keys.deleteById("key");
        assertThrows(SecurityException.class,()->owner.apply(prepared));assertEquals(before,base.root());
    }
    @Test void ownerChangeBetweenPreparationAndApplicationCannotApply(){
        var prepared=prepare();base.base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),new Document("$set",new Document("authorId","other")));
        assertThrows(SecurityException.class,()->owner.apply(prepared));assertEquals(2,base.root().getList("versions",Document.class).size());
    }
    net.modtale.service.project.version.VersionService deletionService(boolean retained,
            net.modtale.service.project.lifecycle.ProjectDeletionService files,net.modtale.service.project.query.ProjectService projects){
        var mongo=base.base.base.fixture.mongo;var access=mock(net.modtale.service.project.access.ProjectAccessService.class);
        when(access.requireVersionPermission(anyString(),any(),eq("VERSION_DELETE"),anyString())).thenAnswer(call->mongo.findById(call.getArgument(0),net.modtale.model.project.Project.class));
        var versions=mock(net.modtale.service.project.access.ProjectVersionAccessService.class);
        when(versions.requireById(any(),anyString(),any())).thenAnswer(call->{var project=(net.modtale.model.project.Project)call.getArgument(0);return project.getVersions().stream().filter(v->v.getId().equals(call.getArgument(1))).findFirst().orElseThrow();});
        var service=new net.modtale.service.project.version.VersionService(new ProjectReviewPersistence(mongo),projects,access,
                new net.modtale.service.project.access.ProjectMutationGuard(),versions,mongo,mock(net.modtale.service.project.version.VersionManifestService.class),files,
                mock(net.modtale.service.project.version.VersionCreationCommandHandler.class),mock(net.modtale.service.project.version.VersionUpdateCommandHandler.class));
        if(retained)org.springframework.test.util.ReflectionTestUtils.setField(service,"retainedMutations",owner);return service;
    }
    @Test void productionDeletionServiceRetainsOriginalRemoteHistoryAndArtifact(){
        var files=mock(net.modtale.service.project.lifecycle.ProjectDeletionService.class);var projects=mock(net.modtale.service.project.query.ProjectService.class);
        Object id=base.root().get("_id");var original=VersionMutationPreparationTest.bytes(base.root());
        deletionService(true,files,projects).deleteVersion(id.toString(),"v",user);
        assertEquals(List.of("w"),base.root().getList("versions",Document.class).stream().map(v->v.getString("_id")).toList());
        var reader=new ProjectMutationReferenceReader(base.base.base.fixture.mongo,base.base.base.archive,base.base.service,base.executor());
        var page=reader.page(id,null,64,()->true);assertEquals(1,page.operationIds().size());
        assertArrayEquals(original,reader.read(id,page.operationIds().getFirst(),()->true).evidence().before().versionBytes());
        verifyNoInteractions(files);verify(projects).evictProjectCache(any(net.modtale.model.project.Project.class));
        assertEquals(0,base.base.base.fixture.posts.get());assertEquals(0,base.base.base.fixture.gets.get());
    }
    @Test void disabledRuntimeCannotDeleteVersionWithLiveReviewBinding(){
        var files=mock(net.modtale.service.project.lifecycle.ProjectDeletionService.class);var projects=mock(net.modtale.service.project.query.ProjectService.class);var before=base.root();
        assertThrows(net.modtale.exception.InvalidVersionRequestException.class,()->deletionService(false,files,projects).deleteVersion(before.get("_id").toString(),"v",user));
        assertEquals(before,base.root());verifyNoInteractions(files,projects);
    }

    net.modtale.service.project.version.VersionUpdateCommandHandler editHandler(boolean retained,
            net.modtale.service.project.version.VersionMutationOrchestrationService orchestration){
        var mongo=base.base.base.fixture.mongo;var access=mock(net.modtale.service.project.access.ProjectAccessService.class);
        when(access.requireVersionPermission(anyString(),any(),eq("VERSION_EDIT"),anyString())).thenAnswer(call->mongo.findById(call.getArgument(0),net.modtale.model.project.Project.class));
        var versions=mock(net.modtale.service.project.access.ProjectVersionAccessService.class);
        when(versions.requireById(any(),anyString(),any())).thenAnswer(call->{var project=(net.modtale.model.project.Project)call.getArgument(0);return project.getVersions().stream().filter(v->v.getId().equals(call.getArgument(1))).findFirst().orElseThrow();});
        when(orchestration.sanitizeChangelog(anyString())).thenAnswer(call->call.getArgument(0));
        var handler=new net.modtale.service.project.version.VersionUpdateCommandHandler(new ProjectReviewPersistence(mongo),mock(net.modtale.service.project.query.ProjectService.class),access,
                new net.modtale.service.project.access.ProjectMutationGuard(),versions,orchestration);
        if(retained)org.springframework.test.util.ReflectionTestUtils.setField(handler,"retainedMutations",owner);return handler;
    }
    @Test void productionContextEditRetainsOldJobAndPreservesBlock(){
        base.base.base.fixture.change("scanResult.verdict","BLOCK");var before=VersionMutationPreparationTest.bytes(base.root());
        var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        editHandler(true,orchestration).updateVersion(base.root().get("_id").toString(),"v",null,null,List.of("changed-runtime"),null,null,user);
        var version=base.root().getList("versions",Document.class).getFirst();var scan=version.get("scanResult",Document.class);
        assertEquals("MUTATION_HELD",scan.get("scanState"));assertEquals(2,scan.get("scanAttempt"));assertEquals("BLOCK",scan.get("verdict"));assertNotNull(version.get("replacementSecurityHold"));
        var ref=base.base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).find().first();
        assertArrayEquals(before,base.base.base.archive.load(ref.getString("beforeArchiveId")).versionBytes());
        verify(orchestration,never()).enqueueContextChangeScan(any(),any());assertEquals(0,base.base.base.fixture.posts.get());
    }
    @Test void metadataOnlyEditKeepsExistingReviewWithoutRetainedMutation(){
        var scan=base.root().getList("versions",Document.class).getFirst().get("scanResult");var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        editHandler(true,orchestration).updateVersion(base.root().get("_id").toString(),"v",null,null,null,"New changelog",null,user);
        var version=base.root().getList("versions",Document.class).getFirst();assertEquals(scan,version.get("scanResult"));assertEquals("New changelog",version.get("changelog"));
        assertEquals(0,base.base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());verify(orchestration,never()).enqueueContextChangeScan(any(),any());
    }
    @Test void disabledRuntimeCannotOverwriteTrackedContext(){
        var before=base.root();var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        assertThrows(net.modtale.exception.InvalidVersionRequestException.class,()->editHandler(false,orchestration).updateVersion(before.get("_id").toString(),"v",null,null,List.of("changed"),null,null,user));
        assertEquals(before,base.root());verify(orchestration,never()).enqueueContextChangeScan(any(),any());
    }
    @Test void modpackContextEditInvalidatesCacheWithoutDeletingHistoricalArchive(){
        var mongo=base.base.base.fixture.mongo;mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),new Document("$set",new Document("classification","MODPACK")));
        var dependency=new net.modtale.model.project.ProjectDependency("dependency","Original dependency","1.0");
        base.base.base.fixture.change("dependencies",List.of(mongo.getConverter().convertToMongoType(dependency)));
        var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        when(orchestration.resolveRequestedDependencies(any(),eq(true),eq(true))).thenReturn(new net.modtale.service.project.version.VersionDependencyService.ResolvedDependencies(List.of(),List.of()));
        editHandler(true,orchestration).updateVersion(base.root().get("_id").toString(),"v",List.of(),null,null,null,null,user);
        var version=base.root().getList("versions",Document.class).getFirst();assertNull(version.get("fileUrl"));assertEquals("MUTATION_HELD",version.get("scanResult",Document.class).get("scanState"));
        verify(orchestration,never()).deleteCachedArtifact(anyString());
    }

    net.modtale.service.project.lifecycle.ProjectDraftWorkflowService submissionService(boolean retained,
            net.modtale.service.project.version.VersionMutationOrchestrationService orchestration,net.modtale.service.communication.WebhookService webhooks){
        var mongo=base.base.base.fixture.mongo;var projects=mock(net.modtale.repository.project.ProjectRepository.class);
        when(projects.findById(anyString())).thenAnswer(call->Optional.ofNullable(mongo.findById(call.getArgument(0),net.modtale.model.project.Project.class)));
        var access=mock(net.modtale.service.project.access.ProjectAccessService.class);
        when(access.requireProjectPermission(anyString(),any(),eq("PROJECT_STATUS_SUBMIT"),anyString())).thenAnswer(call->mongo.findById(call.getArgument(0),net.modtale.model.project.Project.class));
        var service=new net.modtale.service.project.lifecycle.ProjectDraftWorkflowService(projects,mock(net.modtale.service.project.query.ProjectService.class),
                mock(net.modtale.service.project.validation.ValidationService.class),webhooks,mock(net.modtale.service.security.validation.SanitizationService.class),mock(UserRepository.class),access,
                new net.modtale.service.project.access.ProjectMutationGuard(),orchestration,new net.modtale.config.properties.AppLimitProperties(10,5,10,5,5,5,20,10),new ProjectReviewPersistence(mongo));
        if(retained)org.springframework.test.util.ReflectionTestUtils.setField(service,"retainedMutations",owner);return service;
    }
    void draft(){
        user.setEmailVerified(true);base.base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),
                new Document("$set",new Document("status","DRAFT").append("description","A complete project description").append("tags",List.of("utility")).append("license","MIT")));
    }
    @Test void productionSubmissionPreservesPrunedHistoryAndAttemptWithOnlySubmissionScope(){
        draft();api(Set.of(ApiKey.ApiPermission.PROJECT_STATUS_SUBMIT));var fixture=base.base.base.fixture;
        fixture.change("retainedRemoteReview",fixture.mongo.getConverter().convertToMongoType(fixture.saved().getRemoteReview()));fixture.change("scanResult",null);
        var before=VersionMutationPreparationTest.bytes(base.root());var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);var webhooks=mock(net.modtale.service.communication.WebhookService.class);
        submissionService(true,orchestration,webhooks).submitProject(base.root().get("_id").toString(),user);
        assertEquals("PENDING",base.root().get("status"));var version=base.root().getList("versions",Document.class).getFirst();
        assertEquals(2,version.get("scanResult",Document.class).get("scanAttempt"));assertEquals("MUTATION_HELD",version.get("scanResult",Document.class).get("scanState"));
        var ref=fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).find().first();assertArrayEquals(before,base.base.base.archive.load(ref.getString("beforeArchiveId")).versionBytes());
        verifyNoInteractions(orchestration,webhooks);
    }
    @Test void disabledRuntimeCannotSubmitTrackedVersions(){
        draft();var before=base.root();var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);var webhooks=mock(net.modtale.service.communication.WebhookService.class);
        assertThrows(net.modtale.exception.InvalidProjectRequestException.class,()->submissionService(false,orchestration,webhooks).submitProject(before.get("_id").toString(),user));
        assertEquals(before,base.root());verifyNoInteractions(orchestration,webhooks);
    }
    @Test void emailVerificationLossPreventsPreparedSubmission(){
        draft();var captured=base.base.service.capture(base.root().get("_id"),()->true);var proposed=base.root();proposed.put("status","PENDING");
        var prepared=owner.prepare(UUID.randomUUID().toString(),captured.projectId(),captured.sha256(),ProjectMutationPreparation.Mutation.SUBMISSION,VersionMutationPreparationTest.bytes(proposed));
        user.setEmailVerified(false);assertThrows(SecurityException.class,()->owner.apply(prepared));assertEquals("DRAFT",base.root().get("status"));
    }
    @Test void emptyModpackSubmissionStillNotifiesManualReview(){
        draft();base.base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),new Document("$set",new Document("classification","MODPACK").append("versions",List.of())));
        var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);var webhooks=mock(net.modtale.service.communication.WebhookService.class);
        submissionService(true,orchestration,webhooks).submitProject(base.root().get("_id").toString(),user);
        assertEquals("PENDING",base.root().get("status"));verify(webhooks).triggerAdminNewProjectWebhook(any());verifyNoInteractions(orchestration);
    }

    @Test void legacyUnboundQueuedSubmissionGetsRetainedRequest(){
        draft();String old=UUID.randomUUID().toString();base.base.base.fixture.change("reviewStatus","PENDING");
        base.base.base.fixture.change("scanResult",new Document("status","SCANNING").append("scanState","QUEUED").append("scanAttempt",1).append("scanRequestId",old));
        owner.submitDraft(base.root());var scan=base.root().getList("versions",Document.class).getFirst().get("scanResult",Document.class);
        assertEquals("MUTATION_HELD",scan.get("scanState"));assertEquals(2,scan.get("scanAttempt"));assertNotEquals(old,scan.get("scanRequestId"));
    }
    @Test void submissionDoesNotReplaceAnAlreadyRetainedHeldRequest(){
        draft();var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        editHandler(true,orchestration).updateVersion(base.root().get("_id").toString(),"v",null,null,List.of("changed"),null,null,user);
        var scan=base.root().getList("versions",Document.class).getFirst().get("scanResult");owner.submitDraft(base.root());
        assertEquals(scan,base.root().getList("versions",Document.class).getFirst().get("scanResult"));
    }

    net.modtale.service.project.version.VersionCreationCommandHandler uploadHandler(boolean retained,
            net.modtale.service.project.version.VersionMutationOrchestrationService orchestration){
        var mongo=base.base.base.fixture.mongo;var access=mock(net.modtale.service.project.access.ProjectAccessService.class);
        when(access.requireVersionPermission(anyString(),any(),eq("VERSION_CREATE"),anyString())).thenAnswer(call->mongo.findById(call.getArgument(0),net.modtale.model.project.Project.class));
        var handler=new net.modtale.service.project.version.VersionCreationCommandHandler(new ProjectReviewPersistence(mongo),mock(net.modtale.service.project.query.ProjectService.class),access,
                new net.modtale.service.project.access.ProjectMutationGuard(),orchestration,new net.modtale.config.properties.AppLimitProperties(10,5,10,5,5,5,20,10));
        if(retained)org.springframework.test.util.ReflectionTestUtils.setField(handler,"retainedMutations",owner);return handler;
    }
    void uploadFixture(){
        base.base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.root().get("_id")),new Document("$set",new Document("classification","DATA")));
        base.base.base.fixture.change("versionNumber","1.0");base.base.base.fixture.change("gameVersions",List.of("a","b"));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void productionUploadRetainsFullAndPartialReplacementHistory(boolean partial){
        uploadFixture();base.base.base.fixture.change("scanResult.verdict","BLOCK");var before=VersionMutationPreparationTest.bytes(base.root());
        var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        when(orchestration.prepareVersionArtifact(any(),any())).thenReturn(new net.modtale.service.project.version.VersionArtifactService.PreparedVersionArtifact(net.modtale.model.project.ProjectClassification.DATA,"new.zip","d".repeat(64)));
        uploadHandler(true,orchestration).addVersion(base.root().get("_id").toString(),"1.0",partial?List.of("a"):List.of("a","b"),
                new org.springframework.mock.web.MockMultipartFile("file","new.zip","application/zip",new byte[]{1}),null,null,null,null,true,user);
        var versions=base.root().getList("versions",Document.class);assertEquals(partial?3:2,versions.size());assertEquals("new.zip",versions.getFirst().get("fileUrl"));
        assertEquals("MUTATION_HELD",versions.getFirst().get("scanResult",Document.class).get("scanState"));
        if(partial){var old=versions.stream().filter(v->"v".equals(v.get("_id"))).findFirst().orElseThrow();assertEquals(List.of("b"),old.get("gameVersions"));assertEquals("BLOCK",old.get("scanResult",Document.class).get("verdict"));assertEquals(2,old.get("scanResult",Document.class).get("scanAttempt"));}
        var ref=base.base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).find().first();assertArrayEquals(before,base.base.base.archive.load(ref.getString("beforeArchiveId")).versionBytes());
        verify(orchestration,never()).enqueueInitialScan(any(),any(),any(),anyBoolean(),any());verify(orchestration,never()).enqueueContextChangeScan(any(),any());verify(orchestration,never()).deleteVersionFile(any());
    }
    @Test void disabledRuntimeRejectsTrackedReplacementBeforeUpload(){
        uploadFixture();var before=base.root();var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        assertThrows(net.modtale.exception.InvalidVersionRequestException.class,()->uploadHandler(false,orchestration).addVersion(before.get("_id").toString(),"1.0",List.of("a"),null,null,null,null,null,true,user));
        assertEquals(before,base.root());verify(orchestration,never()).prepareVersionArtifact(any(),any());
    }
    @Test void validatedUploadCanChangeDataProjectToPlugin(){
        uploadFixture();var orchestration=mock(net.modtale.service.project.version.VersionMutationOrchestrationService.class);
        when(orchestration.prepareVersionArtifact(any(),any())).thenAnswer(call->{((net.modtale.model.project.Project)call.getArgument(0)).setClassification(net.modtale.model.project.ProjectClassification.PLUGIN);
            return new net.modtale.service.project.version.VersionArtifactService.PreparedVersionArtifact(net.modtale.model.project.ProjectClassification.PLUGIN,"new.jar","d".repeat(64));});
        uploadHandler(true,orchestration).addVersion(base.root().get("_id").toString(),"2.0",List.of("a"),new org.springframework.mock.web.MockMultipartFile("file","new.jar","application/java-archive",new byte[]{1}),null,null,null,null,false,user);
        assertEquals("PLUGIN",base.root().get("classification"));assertEquals("new.jar",base.root().getList("versions",Document.class).getFirst().get("fileUrl"));
    }

}
