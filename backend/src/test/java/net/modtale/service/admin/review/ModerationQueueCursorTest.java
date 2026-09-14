package net.modtale.service.admin.review;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ModerationQueueCursorTest {
    @Test void typedIdentityAndUnicodeRoundTripWithoutStringObjectIdAliasing() {
        var objectId=new ObjectId();
        for(Object id:new Object[]{objectId,objectId.toHexString(),"😀".repeat(128),"a\u0000b"}) {
            var cursor=new ModerationQueuePageReader.Cursor(id,16777216);
            assertEquals(cursor,ModerationQueueCursor.decode(ModerationQueueCursor.encode(cursor)));
        }
        assertNotEquals(ModerationQueueCursor.encode(new ModerationQueuePageReader.Cursor(objectId,0)),ModerationQueueCursor.encode(new ModerationQueuePageReader.Cursor(objectId.toHexString(),0)));
        assertNull(ModerationQueueCursor.decode(null));assertNull(ModerationQueueCursor.encode(null));
    }
    @Test void noncanonicalOversizedInvalidUtf8AndOutOfRangeCursorsAreRejected() {
        for(String token:new String[]{"","2.s.0.YQ","1.s.-1.YQ","1.s.00.YQ","1.s.16777217.YQ","1.s.0.YQ==","1.s.0.YR","1.s.0._w","1.o.0.YQ","x".repeat(801),"1.s.0."+Base64.getUrlEncoder().withoutPadding().encodeToString("x".repeat(129).getBytes(StandardCharsets.UTF_8))})
            assertThrows(IllegalArgumentException.class,()->ModerationQueueCursor.decode(token));
    }
}
