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

}
