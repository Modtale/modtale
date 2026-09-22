package net.modtale.service.auth;

import com.mongodb.client.*;
import java.util.*;
import java.time.LocalDateTime;
import net.modtale.model.user.ApiKey;
import net.modtale.repository.user.ApiKeyRepository;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ApiKeyScopedWritesIntegrationTest {
    MongoClient client;MongoTemplate mongo;ApiKeyRepository keys;ApiKey key;
    @BeforeEach void setup(){
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException("Unexpected test port");
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");
        mongo=new MongoTemplate(client,"warden_key_writes_"+UUID.randomUUID().toString().replace("-",""));
        keys=new MongoRepositoryFactory(mongo).getRepository(ApiKeyRepository.class);
        key=new ApiKey("owner","fixture","fixture-hash","fixture-prefix");key.setId("key");
        key.setContextPermissions(Map.of("project",Set.of(ApiKey.ApiPermission.VERSION_CREATE,ApiKey.ApiPermission.VERSION_DELETE)));keys.insert(key);
        mongo.getCollection("api_keys").updateOne(new Document("_id","key"),new Document("$set",new Document("futureAuthority","preserve")));
    }
    @AfterEach void cleanup(){if(client!=null){mongo.getDb().drop();client.close();}}
    @Test void delayedWritesCannotRecreateRevokedKey(){
        var before=key.getContextPermissions();keys.deleteById(key.getId());
        assertEquals(0,keys.recordUse("key","owner","fixture-hash",LocalDateTime.now()));
        assertEquals(0,keys.restrictContexts("key","owner","fixture-hash",before,Map.of()));assertFalse(keys.existsById("key"));
    }
    @Test void timestampWritePreservesConcurrentPermissionsAndUnknownFields(){
        var restricted=Map.of("project",Set.of(ApiKey.ApiPermission.VERSION_CREATE));
        assertEquals(1,keys.restrictContexts("key","owner","fixture-hash",key.getContextPermissions(),restricted));
        var later=LocalDateTime.now();assertEquals(1,keys.recordUse("key","owner","fixture-hash",later));
        assertEquals(0,keys.recordUse("key","owner","fixture-hash",later.minusDays(1)));
        assertEquals(restricted,keys.findById("key").orElseThrow().getContextPermissions());
        assertEquals("preserve",mongo.getCollection("api_keys").find().first().get("futureAuthority"));
    }
    @Test void stalePruningCannotUndoNewerRevocationOrCredentialChange(){
        var original=key.getContextPermissions();assertEquals(1,keys.restrictContexts("key","owner","fixture-hash",original,Map.of()));
        assertEquals(0,keys.restrictContexts("key","owner","fixture-hash",original,Map.of("project",Set.of(ApiKey.ApiPermission.VERSION_CREATE))));
        mongo.getCollection("api_keys").updateOne(new Document("_id","key"),new Document("$set",new Document("keyHash","new-hash")));
        assertEquals(0,keys.recordUse("key","owner","fixture-hash",LocalDateTime.now()));
        assertEquals(0,keys.restrictContexts("key","owner","fixture-hash",Map.of(),original));assertTrue(keys.findById("key").orElseThrow().getContextPermissions().isEmpty());
    }
    @Test void actualQueuedUsageTaskCannotUndoRevocation(){
        String plain="md_fixture-security-test";key.setPrefix(plain.substring(0,10));key.setKeyHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(plain));keys.save(key);
        var queue=new ArrayList<Runnable>();var service=new ApiKeyResolutionService(keys,org.mockito.Mockito.mock(net.modtale.repository.user.UserRepository.class),
                org.mockito.Mockito.mock(ApiKeyIssuanceService.class),queue::add);
        assertNotNull(service.resolveKey(plain));assertEquals(1,queue.size());keys.deleteById("key");queue.getFirst().run();assertFalse(keys.existsById("key"));
    }

}
