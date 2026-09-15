package net.modtale.service.user.organization;
import com.mongodb.client.*;
import java.util.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import net.modtale.model.user.*;
import net.modtale.repository.user.*;
import net.modtale.service.communication.*;
import net.modtale.service.security.access.AccessControlService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST", matches="true")
class OrganizationInvitePersistenceIntegrationTest {
    private MongoClient client; private MongoTemplate mongo; private String database;
    private OrganizationInvitePersistence writes;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if (!Set.of("27029","27030").contains(port)) throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        database="warden_org_invite_"+UUID.randomUUID().toString().replace("-", "");
        mongo=new MongoTemplate(client,database); writes=new OrganizationInvitePersistence(mongo);
        var org=new User(); org.setId("org"); org.setAccountType(User.AccountType.ORGANIZATION);
        var owner=new User.OrganizationRole("owner-role","Owner","#fff",Set.of()); owner.setOwner(true);
        org.setOrganizationRoles(new ArrayList<>(List.of(owner,new User.OrganizationRole("role","Member","#fff",Set.of(ApiKey.ApiPermission.PROJECT_CREATE)))));
        org.setOrganizationMembers(new ArrayList<>(List.of(new User.OrganizationMember("owner","owner-role"))));
        org.setLikedModIds(List.of("favorite")); mongo.insert(org);
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("unknownAuthority","retained")
                .append("organizationMembers.0.futurePermissionField","retained")));
    }
    @AfterEach void cleanup(){if(client!=null){client.getDatabase(database).drop();client.close();}}
    private User.OrganizationMember invitation(String id,long expiry){
        var invite=new User.OrganizationMember("recipient","role"); invite.setRequestId(id); invite.setRequestExpiresAt(expiry);
        invite.setRequestPermissions(Set.of(ApiKey.ApiPermission.PROJECT_CREATE)); invite.setRequestOwnerIds(Set.of("owner")); return invite;
    }
    private void issue(String id,long expiry){assertTrue(writes.create(writes.capture("org"),invitation(id,expiry)));}
    private Document stored(){return mongo.getCollection("users").find(new Document("_id","org")).first();}
    @Test void createAndAcceptPreserveAccountFieldsAndUnknownMembershipFields(){
        var snapshot=writes.capture("org"); snapshot.organization().setRoles(List.of("FULL_ADMIN")); snapshot.organization().setLikedModIds(List.of());
        assertTrue(writes.create(snapshot,invitation("invite",System.currentTimeMillis()+60000)));
        assertEquals(List.of("USER"),stored().get("roles")); assertEquals(List.of("favorite"),stored().get("likedModIds"));
        assertTrue(writes.resolve(writes.capture("org"),"recipient","invite",true));
        assertEquals(2,stored().getList("organizationMembers",Document.class).size());
        assertEquals("retained",stored().getList("organizationMembers",Document.class).getFirst().get("futurePermissionField"));
        assertEquals("retained",stored().get("unknownAuthority")); assertEquals(List.of(),stored().get("pendingOrgInvites"));
    }
    @Test void concurrentAccountChangePreventsCreationAndStaleCancellation(){
        var snapshot=writes.capture("org");
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("roles",List.of("MODERATOR"))));
        assertFalse(writes.create(snapshot,invitation("invite",System.currentTimeMillis()+60000)));
        issue("old",System.currentTimeMillis()+60000); var old=writes.capture("org");
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("pendingOrgInvites.0.requestId","new")));
        assertFalse(writes.resolve(old,"recipient","old",false));
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.resolve(writes.capture("org"),"recipient","legacy",false));
    }
    @Test void changedPermissionsOwnershipAndExpiryCannotGrantMembership(){
        issue("invite",1);
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.resolve(writes.capture("org"),"recipient","invite",true));
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("pendingOrgInvites.0.requestExpiresAt",System.currentTimeMillis()+60000)
                .append("organizationRoles.1.permissions",List.of())));
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.resolve(writes.capture("org"),"recipient","invite",true));
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("organizationRoles.1.permissions",List.of("PROJECT_CREATE"))
                .append("organizationMembers.0.userId","different-owner")));
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.resolve(writes.capture("org"),"recipient","invite",true));
        assertEquals(1,stored().getList("organizationMembers",Document.class).size());
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("organizationMembers.0.userId","owner")
                .append("organizationMembers.0.roleId","role").append("organizationMembers.0.role","OWNER")));
        assertTrue(OrganizationInvitationPolicy.owners(writes.capture("org").organization()).isEmpty());
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.resolve(writes.capture("org"),"recipient","invite",true));
    }
    @Test void oldNotificationsAndExpiryCannotRemoveReplacementInvites(){
        issue("new",System.currentTimeMillis()+60000);
        var repository=mock(NotificationRepository.class);
        var notifications=new NotificationService(repository,mock(UserRepository.class),mongo,mock(NotificationDeliveryService.class));
        var old=new Notification("recipient","Invite","Invite",java.net.URI.create("/dashboard"),null,NotificationType.ORG_INVITE,
                Map.of("orgId","org","requestId","old")); old.setId("old"); when(repository.findById("old")).thenReturn(Optional.of(old));
        notifications.deleteNotification("old","recipient"); notifications.cleanupExpiredOrganizationInvites();
        assertEquals("new",stored().getList("pendingOrgInvites",Document.class).getFirst().getString("requestId"));
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("pendingOrgInvites.0.requestExpiresAt",1L)));
        notifications.cleanupExpiredOrganizationInvites(); assertEquals(List.of(),stored().get("pendingOrgInvites"));
    }
    @Test void deletedOrganizationsAndUnknownIdentitiesFailClosed(){
        issue(null,0); assertTrue(writes.resolve(writes.capture("org"),"recipient","legacy",false));
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.capture("missing"));
        mongo.getCollection("users").updateOne(new Document("_id","org"),new Document("$set",new Document("deletedAt",new java.util.Date())));
        assertThrows(net.modtale.exception.InvalidOrganizationRequestException.class,()->writes.capture("org"));
    }
    @Test void failedInviteCreationDoesNotNotifyOrSaveTheWholeAccount(){
        var repository=mock(UserRepository.class); var notifications=mock(NotificationService.class); var access=mock(AccessControlService.class);
        var spyWrites=spy(writes); doReturn(false).when(spyWrites).create(any(),any());
        var service=new OrganizationInviteService(repository,new OrganizationAccessService(repository,access),notifications,spyWrites);
        var requester=new User();requester.setId("owner");var target=new User();target.setId("recipient");
        when(repository.findById("recipient")).thenReturn(Optional.of(target));
        when(access.hasOrgPermission(any(),eq("owner"),eq(ApiKey.ApiPermission.ORG_MEMBER_INVITE))).thenReturn(true);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.inviteOrganizationMember("org","recipient","role",requester));
        verifyNoInteractions(notifications);verify(repository,never()).save(any(User.class));assertEquals(List.of(),stored().get("pendingOrgInvites"));
    }
}
