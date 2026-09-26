package net.modtale.service.security.scan;

import com.mongodb.client.*;
import java.util.*;
import net.modtale.model.project.*;
import net.modtale.model.user.*;
import net.modtale.repository.user.*;
import net.modtale.service.admin.review.*;
import net.modtale.service.security.access.*;
import net.modtale.service.user.account.AccountService;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ProjectMutationOwnerAuthorityTest {
    MongoClient client;MongoTemplate mongo;ApiKeyRepository keys;ProjectMutationOwnerAuthority authority;User user;AccountService accounts;
    static final ApiKey.ApiPermission EDIT=ApiKey.ApiPermission.VERSION_EDIT;
    @BeforeEach void setup(){
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");mongo=new MongoTemplate(client,"warden_owner_"+UUID.randomUUID().toString().replace("-",""));
        keys=new MongoRepositoryFactory(mongo).getRepository(ApiKeyRepository.class);accounts=mock(AccountService.class);
        user=new User();user.setId("owner");when(accounts.getCurrentUser(any())).thenAnswer(call->user);
        var project=new Project();project.setId("project");project.setAuthorId("owner");project.setStatus(ProjectStatus.PUBLISHED);mongo.insert(project);
        var access=new AccessControlService(accounts,mock(UserRepository.class),mock(PermissionProjectLookupService.class),mongo);
        authority=new ProjectMutationOwnerAuthority(accounts,access,keys,mongo);authenticate(false);
    }
    void authenticate(boolean api){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,api?List.of(new SimpleGrantedAuthority("ROLE_API")):List.of()));}
    void key(Set<ApiKey.ApiPermission> permissions){
        String plain="md_authority-fixture";var key=new ApiKey("owner","fixture",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(plain),plain.substring(0,10));
        key.setId("key");key.setContextPermissions(Map.of("project",permissions));keys.insert(key);
        var request=new MockHttpServletRequest();request.addHeader("X-MODTALE-KEY",plain);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));authenticate(true);
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();if(client!=null){mongo.getDb().drop();client.close();}}
    @Test void browserOwnerIsBoundToCurrentIdentityAndProject(){
        var bound=authority.bind("project",Set.of(EDIT));assertEquals("owner",bound.actor());assertTrue(bound.permitted().getAsBoolean());
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("authorId","other")));
        assertFalse(bound.permitted().getAsBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"revoked","scope","credential","owner","membership","deletedAccount","authentication"})
    void keyAuthorityRechecksEveryMutableGrant(String change){
        key(Set.of(EDIT));var bound=authority.bind("project",Set.of(EDIT));assertTrue(bound.permitted().getAsBoolean());
        switch(change){
            case "revoked" -> keys.deleteById("key");
            case "scope" -> mongo.getCollection("api_keys").updateOne(new Document("_id","key"),new Document("$set",new Document("contextPermissions",new Document())));
            case "credential" -> mongo.getCollection("api_keys").updateOne(new Document("_id","key"),new Document("$set",new Document("keyHash","changed")));
            case "owner" -> mongo.getCollection("api_keys").updateOne(new Document("_id","key"),new Document("$set",new Document("userId","other")));
            case "membership" -> mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("authorId","other")));
            case "deletedAccount" -> when(accounts.getCurrentUser(any())).thenReturn(null);
            case "authentication" -> authenticate(true);
        }
        assertFalse(bound.permitted().getAsBoolean());
    }
    @Test void keyScopeDoesNotReplaceMembershipAndOwnerDoesNotReplaceKeyScope(){
        key(Set.of(ApiKey.ApiPermission.VERSION_READ));assertThrows(SecurityException.class,()->authority.bind("project",Set.of(EDIT)));
    }
    @Test void authenticationWithoutRequestKeyCannotBorrowOwnerAuthority(){
        authenticate(true);assertThrows(SecurityException.class,()->authority.bind("project",Set.of(EDIT)));
    }
    @Test void repairPermissionAndDeletedProjectsCannotBeUsedForOwnerMutations(){
        assertThrows(SecurityException.class,()->authority.bind("project",Set.of(ApiKey.ApiPermission.PROJECT_DELETE)));
        mongo.getCollection("projects").updateOne(new Document("_id","project"),new Document("$set",new Document("status","DELETED")));
        assertThrows(SecurityException.class,()->authority.bind("project",Set.of(EDIT)));
    }
}
