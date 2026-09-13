package net.modtale.service.auth;

import com.mongodb.client.*;
import java.util.*;
import net.modtale.model.user.User;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.ResourceNotFoundException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST", matches="true")
class MfaEnrollmentIntegrationTest {
    private MongoClient client; private MongoTemplate mongo; private String database;
    private TwoFactorService factors; private MfaEnrollmentService service;
    @BeforeEach void setup(){
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        database="warden_mfa_test_"+UUID.randomUUID().toString().replace("-", "");
        mongo=new MongoTemplate(client,database);factors=mock(TwoFactorService.class);service=new MfaEnrollmentService(mongo,factors);
        var user=new User();user.setId("user");user.setLikedModIds(List.of("favorite"));mongo.insert(user);
        change(new Document("unknownAuthority","retained"));
        when(factors.generateNewSecret()).thenReturn("fixture-secret");
        when(factors.isOtpValid("fixture-secret","123456")).thenReturn(true);
    }
    @AfterEach void cleanup(){if(client!=null){client.getDatabase(database).drop();client.close();}}
    private Document stored(){return mongo.getCollection("users").find(new Document("_id","user")).first();}
    private void change(Document fields){mongo.getCollection("users").updateOne(new Document("_id","user"),new Document("$set",fields));}
    @Test void enrollmentOnlyChangesFactorFieldsAndCannotReplaceEnabledFactor(){
        var before=stored();assertEquals("fixture-secret",service.begin("user"));service.verify("user","123456");
        var after=stored();assertEquals(true,after.remove("mfaEnabled"));assertEquals("fixture-secret",after.remove("mfaSecret"));
        before.remove("mfaEnabled");before.remove("mfaSecret");assertEquals(before,after);
        assertThrows(InvalidAuthenticationRequestException.class,()->service.begin("user"));
        assertThrows(InvalidAuthenticationRequestException.class,()->service.verify("user","123456"));
        assertEquals("fixture-secret",stored().getString("mfaSecret"));
    }
    @Test void verificationCannotEnableAReplacedSecret(){
        service.begin("user");
        when(factors.isOtpValid("fixture-secret","123456")).thenAnswer(invocation->{change(new Document("mfaSecret","replacement"));return true;});
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.verify("user","123456")).getStatusCode().value());
        assertEquals("replacement",stored().getString("mfaSecret"));assertEquals(false,stored().get("mfaEnabled"));
    }
    @Test void verificationCannotUndoConcurrentAuthorityDeletionOrFavorites(){
        for(var mutation:List.of(new Document("roles",List.of("MODERATOR")),new Document("deletedAt",new Date()),
                new Document("likedModIds",List.of("new")),new Document("mfaEnabled",true))){
            var initial=stored();service.begin("user");
            doAnswer(invocation->{change(mutation);return true;}).when(factors).isOtpValid("fixture-secret","123456");
            assertThrows(ResponseStatusException.class,()->service.verify("user","123456"));
            mutation.forEach((key,value)->assertEquals(value,stored().get(key)));
            assertEquals(mutation.getOrDefault("mfaEnabled",false),stored().get("mfaEnabled"));
            mongo.getCollection("users").replaceOne(new Document("_id","user"),initial);
        }
    }
    @Test void delayedSetupCannotReplaceAConcurrentlyEnabledFactor(){
        when(factors.generateNewSecret()).thenAnswer(invocation->{change(new Document("mfaEnabled",true).append("mfaSecret","enabled-secret"));return "new-secret";});
        assertThrows(ResponseStatusException.class,()->service.begin("user"));
        assertEquals(true,stored().get("mfaEnabled"));assertEquals("enabled-secret",stored().get("mfaSecret"));
    }
    @Test void invalidCodeMissingSecretAndMalformedStateFailClosed(){
        assertThrows(InvalidAuthenticationRequestException.class,()->service.verify("user","123456"));
        service.begin("user");assertThrows(InvalidAuthenticationRequestException.class,()->service.verify("user","wrong"));
        assertEquals(false,stored().get("mfaEnabled"));
        change(new Document("mfaEnabled","false"));
        assertThrows(InvalidAuthenticationRequestException.class,()->service.begin("user"));
        assertThrows(InvalidAuthenticationRequestException.class,()->service.verify("user","123456"));
    }
    @Test void deletedMissingAndAmbiguousIdentitiesCannotEnroll(){
        assertThrows(ResourceNotFoundException.class,()->service.begin("missing"));
        change(new Document("deletedAt",new Date()));assertThrows(ResourceNotFoundException.class,()->service.begin("user"));
        var id=new ObjectId();mongo.getCollection("users").insertOne(new Document("_id",id));
        assertEquals("fixture-secret",service.begin(id.toHexString()));service.verify(id.toHexString(),"123456");
        mongo.getCollection("users").insertOne(new Document("_id",id.toHexString()));
        assertThrows(ResourceNotFoundException.class,()->service.begin(id.toHexString()));
    }
}
