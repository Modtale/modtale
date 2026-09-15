package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReviewStateDiagnosticCursorTest {
    @Test void exactBsonIdentityAndPhysicalPositionRoundTrip() {
        for(Object id:List.of("a",new ObjectId("abcdefabcdefabcdefabcdef"),"abcdefabcdefabcdefabcdef","\n$position","😀".repeat(128)))
            for(boolean after:List.of(false,true)) {
                var cursor=new RemoteReviewDiscovery.Cursor(id,after?0:16777216,after);
                assertEquals(cursor,ReviewStateDiagnosticCursor.decode(ReviewStateDiagnosticCursor.encode(cursor)));
            }
        assertNull(ReviewStateDiagnosticCursor.decode(null));assertNull(ReviewStateDiagnosticCursor.encode(null));
    }
    @Test void rejectsOtherScopesAndNoncanonicalOrUnboundedTokens() {
        for(String token:List.of("","1.s.0.YQ","d1.v.2.SECURITY.s.0.YQ","d2.v.1.s.0.YQ","d1.a.1.s.1.YQ","d1.v.1.s.00.YQ",
                "d1.v.1.s.0.YQ=","d1.v.1.s.0.YR","d1.v.1.s.0._w","d1.v.1.s.16777217.YQ","d1.v.1.s.0.","d1.v.1.o.0.YQ","d1.v.1.s.0.YQ.extra","x".repeat(811)))
            assertThrows(IllegalArgumentException.class,()->ReviewStateDiagnosticCursor.decode(token),token);
        assertThrows(IllegalArgumentException.class,()->ReviewStateDiagnosticCursor.encode(new RemoteReviewDiscovery.Cursor("\ud800",0,false)));
    }
    @Test void diagnosticScopeCannotBeUsedAsOrdinaryQueueCursor() {
        var token=ReviewStateDiagnosticCursor.encode(new RemoteReviewDiscovery.Cursor("a",0,false));
        assertThrows(IllegalArgumentException.class,()->ModerationQueueCursor.decode(token));
    }
}
