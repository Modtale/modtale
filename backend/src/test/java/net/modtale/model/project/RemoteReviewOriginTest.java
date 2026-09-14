package net.modtale.model.project;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RemoteReviewOriginTest {
    @Test void rejectsMalformedOriginAndPreservesValidOriginOnAttachment() {
        String id="abcdefab-1111-1111-1111-111111111111";var origin=new RemoteReviewOrigin(id,"a".repeat(64));
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewOrigin(id.toUpperCase(),"a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewOrigin(id,null));assertThrows(IllegalArgumentException.class,()->new RemoteReviewOrigin(null,"a".repeat(64)));
        var binding=new RemoteReviewBinding("p","v",UUID.randomUUID().toString(),1,"file","a".repeat(64),"b".repeat(64),"warden-3.0.0:"+"c".repeat(64),"d".repeat(64),null,false,origin);
        assertEquals(origin,binding.withJobId(UUID.randomUUID().toString()).origin());
    }
    @Test void legacyConstructorDoesNotInventAnOrigin() {
        var legacy=new RemoteReviewBinding("p","v",UUID.randomUUID().toString(),1,"file","a".repeat(64),"b".repeat(64),"warden-3.0.0:"+"c".repeat(64),"d".repeat(64),null);
        assertNull(legacy.origin());assertNull(legacy.withJobId(UUID.randomUUID().toString()).origin());
    }
}
