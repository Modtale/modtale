package net.modtale.service.user.account;

import com.mongodb.client.*;
import java.util.*;
import net.modtale.model.user.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST", matches="true")
class AccountPreferencesPersistenceIntegrationTest {
    private MongoClient client;
    private MongoTemplate mongo;
    private String database;
    private AccountPreferencesPersistence writes;

    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port)) throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        database="warden_preferences_test_"+UUID.randomUUID().toString().replace("-", "");
        mongo=new MongoTemplate(client,database);
        writes=spy(new AccountPreferencesPersistence(mongo));
        var user=new User();user.setId("user");user.setUsername("Original");
        user.setGithubAccessToken("fixture-token");user.setLikedModIds(List.of("favorite"));
        mongo.insert(user);
        mongo.getCollection("users").updateOne(new Document("_id","user"),
                new Document("$set",new Document("unknownAuthority","retained")));
    }
    @AfterEach void cleanup(){if(client!=null){client.getDatabase(database).drop();client.close();}}
    private Document stored(){return mongo.getCollection("users").find(new Document("_id","user")).first();}
    private AccountService service(){
        return new AccountService(mock(net.modtale.repository.user.UserRepository.class),mongo,
                mock(net.modtale.service.security.validation.SanitizationService.class),
                mock(CurrentUserResolutionService.class),mock(OAuthAvatarHealingService.class),
                mock(AccountLifecycleService.class),
                mock(net.modtale.service.user.connection.ConnectedAccountMutationService.class),writes);
    }
    @Test void eachFieldUpdatePreservesAuthorityFavoritesAndUnknownData(){
        for(var field:AccountPreferencesPersistence.Field.values()){
            var before=stored();var state=writes.capture("user");
            state.user().setRoles(List.of("FULL_ADMIN"));state.user().setLikedModIds(List.of());
            state.user().setGithubAccessToken("wrong");
            state.user().setAvatarUrl("new-avatar");state.user().setBannerUrl("new-banner");
            state.user().setNotificationPreferences(new User.NotificationPreferences());
            state.user().setLauncherSettings(new LauncherSettingsSnapshot());
            assertTrue(writes.update(state,field));
            var after=stored();
            String key=switch(field){case AVATAR->"avatarUrl";case BANNER->"bannerUrl";
                case NOTIFICATIONS->"notificationPreferences";case LAUNCHER->"launcherSettings";};
            assertNotNull(after.get(key));before.remove(key);after.remove(key);assertEquals(before,after);
        }
    }
    @Test void staleSettingsCannotUndoAnyConcurrentAccountWrite(){
        for(var change:List.of(new Document("roles",List.of("MODERATOR")),
                new Document("likedModIds",List.of("new-favorite")),new Document("password","fixture-password"),
                new Document("deletedAt",new Date()),new Document("launcherSettings",new Document("settingsHash","new")))){
            var initial=stored();var state=writes.capture("user");state.user().setBannerUrl("attempted");
            mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",change));
            var expected=stored();
            for(var field:AccountPreferencesPersistence.Field.values())assertFalse(writes.update(state,field));
            assertEquals(expected,stored());
            mongo.getCollection("users").replaceOne(new Document("_id","user"),initial);
        }
    }
    @Test void everySettingsEndpointReportsConcurrentModificationInsteadOfSuccess(){
        var service=service();
        doAnswer(invocation->{
            mongo.getCollection("users").updateOne(new Document("_id","user"),
                    new Document("$set",new Document("concurrentWrite",UUID.randomUUID().toString())));
            return invocation.callRealMethod();
        }).when(writes).update(any(),any());
        List<Runnable> actions=List.of(()->service.updateUserAvatar("user","avatar"),
                ()->service.updateUserBanner("user","banner"),
                ()->service.updateNotificationPreferences("user",new User.NotificationPreferences()),
                ()->service.updateLauncherSettings("user",new LauncherSettingsSnapshot()),
                ()->service.updateLauncherSettingsPreferences("user",new LauncherSettingsSnapshot()));
        for(var action:actions){
            var ex=assertThrows(ResponseStatusException.class,action::run);assertEquals(409,ex.getStatusCode().value());
        }
        assertNull(stored().get("avatarUrl"));assertNull(stored().get("launcherSettings"));
        assertEquals(List.of("USER"),stored().get("roles"));
        assertEquals(List.of("favorite"),stored().get("likedModIds"));
    }
    @Test void realSettingsFlowPreservesInstalledProjectsAndSupportsIdempotentClearing(){
        var service=service();service.updateUserAvatar("user","avatar");service.updateUserBanner("user","banner");
        service.updateUserAvatar("user",null);service.updateUserAvatar("user",null);
        service.updateNotificationPreferences("user",new User.NotificationPreferences());
        var settings=new LauncherSettingsSnapshot();var project=new LauncherSettingsSnapshot.InstalledProject();
        project.setProjectId("project");settings.setInstalledProjects(List.of(project));
        service.updateLauncherSettings("user",settings);
        var update=new LauncherSettingsSnapshot();update.setSettingsHash(" new ");
        service.updateLauncherSettingsPreferences("user",update);
        var current=mongo.findById("user",User.class);
        assertNull(current.getAvatarUrl());assertEquals("banner",current.getBannerUrl());
        assertEquals("new",current.getLauncherSettings().getSettingsHash());
        assertEquals("project",current.getLauncherSettings().getInstalledProjects().getFirst().getProjectId());
        assertEquals(List.of("favorite"),current.getLikedModIds());
    }
    @Test void missingDeletedOrAmbiguousAccountsCannotBeChanged(){
        assertNull(writes.capture("missing"));
        mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",new Document("deletedAt",new Date())));
        assertNull(writes.capture("user"));
        assertThrows(net.modtale.exception.ResourceNotFoundException.class,()->service().updateUserAvatar("user","avatar"));
        var id=new ObjectId();mongo.getCollection("users").insertOne(new Document("_id",id));
        var state=writes.capture(id.toHexString());state.user().setAvatarUrl("avatar");assertTrue(writes.update(state,AccountPreferencesPersistence.Field.AVATAR));
        mongo.getCollection("users").insertOne(new Document("_id",id.toHexString()));assertNull(writes.capture(id.toHexString()));
    }
}
