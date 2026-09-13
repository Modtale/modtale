package net.modtale.service.user.account;

import com.mongodb.client.*;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import net.modtale.model.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST", matches="true")
class UserAvatarPersistenceIntegrationTest {
    private MongoClient client; private MongoTemplate mongo; private String database; private UserAvatarPersistence writes;
    private static final String OLD="https://avatars.githubusercontent.com/old", NEW="https://avatars.githubusercontent.com/new";
    @BeforeEach void setup(){
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        database="warden_avatar_test_"+UUID.randomUUID().toString().replace("-", "");
        mongo=new MongoTemplate(client,database);writes=new UserAvatarPersistence(mongo);
        var user=new User();user.setId("user");user.setUsername("Original");user.setAvatarUrl(OLD);
        user.setGithubAccessToken("fixture-token");user.setLikedModIds(List.of("favorite"));
        user.setConnectedAccounts(List.of(new User.ConnectedAccount(OAuthProvider.GITHUB,"provider-id","name",null,true)));mongo.insert(user);
        mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",new Document("unknownAuthority","retained")));
    }
    @AfterEach void cleanup(){if(client!=null){client.getDatabase(database).drop();client.close();}}
    private Document stored(){return mongo.getCollection("users").find(new Document("_id","user")).first();}
    private User user(){return mongo.findById("user",User.class);}
    private OAuthAvatarHealingService service(){
        var service=spy(new OAuthAvatarHealingService(writes));
        doReturn(false).when(service).isImageUrlReachable(OLD);doReturn(true).when(service).isImageUrlReachable(NEW);
        return service;
    }
    @Test void persistenceCanOnlyChangeAvatarAndPreservesUnknownAccountData(){
        var snapshot=writes.capture("user");snapshot.user().setRoles(List.of("FULL_ADMIN"));snapshot.user().setLikedModIds(List.of());
        assertTrue(writes.update(snapshot,NEW));assertEquals(NEW,stored().getString("avatarUrl"));
        assertEquals(List.of("USER"),stored().get("roles"));assertEquals(List.of("favorite"),stored().get("likedModIds"));
        assertEquals("fixture-token",stored().get("githubAccessToken"));assertEquals("retained",stored().get("unknownAuthority"));
        assertFalse(writes.update(snapshot,OLD));
    }
    @Test void delayedRefreshCannotUndoConcurrentRoleChanges(){
        var caller=user();var service=service();
        doAnswer(invocation->{mongo.getCollection("users").updateOne(new Document("_id","user"),
                new Document("$set",new Document("roles",List.of("MODERATOR"))));return NEW;}).when(service).refreshAvatarFromLinkedProvider(any());
        service.maybeHealOAuthAvatar(caller);
        assertEquals(List.of("MODERATOR"),stored().get("roles"));assertEquals(OLD,stored().get("avatarUrl"));assertEquals(OLD,caller.getAvatarUrl());
    }
    @Test void delayedFallbackCannotUndoDeletionCredentialsFavoritesDisconnectOrAvatarReplacement(){
        var initial=stored();
        var changes=List.of(new Document("deletedAt",new Date()),new Document("password","new-fixture-password"),
                new Document("likedModIds",List.of("new-favorite")),new Document("connectedAccounts",List.of()),new Document("avatarUrl","custom-avatar"));
        for(var change:changes){
            mongo.getCollection("users").replaceOne(new Document("_id","user"),initial);
            var caller=user();var service=service();
            doAnswer(invocation->{mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",change));return null;})
                    .when(service).refreshAvatarFromLinkedProvider(any());
            service.maybeHealOAuthAvatar(caller);
            for(var field:change.keySet())assertEquals(change.get(field),stored().get(field));
            assertEquals(change.getOrDefault("avatarUrl",OLD),stored().get("avatarUrl"));assertEquals(OLD,caller.getAvatarUrl());
        }
    }
    @Test void captureUsesCurrentProviderContextAndEncodesFallbackUsername(){
        var caller=user();
        mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",new Document("username","Fresh & Person").append("githubAccessToken","current-fixture-token")));
        var service=service();
        doAnswer(invocation->{User current=invocation.getArgument(0);assertEquals("Fresh & Person",current.getUsername());
            assertEquals("current-fixture-token",current.getGithubAccessToken());return null;}).when(service).refreshAvatarFromLinkedProvider(any());
        service.maybeHealOAuthAvatar(caller);
        assertEquals("https://ui-avatars.com/api/?name=Fresh+%26+Person&background=random",stored().getString("avatarUrl"));
        assertEquals(stored().get("avatarUrl"),caller.getAvatarUrl());assertEquals("current-fixture-token",stored().get("githubAccessToken"));
    }
    @Test void successfulProviderRefreshUpdatesOnlyTheReturnedAvatar(){
        var caller=user();var service=service();doReturn(NEW).when(service).refreshAvatarFromLinkedProvider(any());
        service.maybeHealOAuthAvatar(caller);assertEquals(NEW,stored().getString("avatarUrl"));assertEquals(NEW,caller.getAvatarUrl());
        assertEquals(List.of("favorite"),stored().get("likedModIds"));
    }
    @Test void disconnectedDeletedAndAmbiguousAccountsAreNotHealed(){
        var caller=user();var service=service();
        mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",new Document("connectedAccounts",List.of())));
        service.maybeHealOAuthAvatar(caller);verify(service,never()).isImageUrlReachable(anyString());verify(service,never()).refreshAvatarFromLinkedProvider(any());
        mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",new Document("deletedAt",new Date())));
        assertNull(writes.capture("user"));assertNull(writes.capture("missing"));
        var id=new ObjectId();mongo.getCollection("users").insertOne(new Document("_id",id));
        assertNotNull(writes.capture(id.toHexString()));mongo.getCollection("users").insertOne(new Document("_id",id.toHexString()));
        assertNull(writes.capture(id.toHexString()));
    }
}
