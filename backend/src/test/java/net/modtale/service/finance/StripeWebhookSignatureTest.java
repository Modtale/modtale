package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeWebhookSignatureTest {
    private String sign(String body, long timestamp) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    private boolean verify(String body, String header, long now) { return StripeWebhookSignature.verify(body.getBytes(StandardCharsets.UTF_8), header, "test-secret", now); }

    @Test void verifiesOriginalBodyAndMultipleSignatures() throws Exception {
        assertTrue(verify("{\"id\":\"evt_1\"}", sign("{\"id\":\"evt_1\"}", 1000) + ",v1=invalid", 1000));
        assertFalse(verify("{ \"id\":\"evt_1\"}", sign("{\"id\":\"evt_1\"}", 1000), 1000));
    }
    @Test void rejectsExpiredFutureMalformedOrAbsentSignatures() throws Exception {
        assertFalse(verify("{}", sign("{}", 1000), 1301));
        assertFalse(verify("{}", sign("{}", 1301), 1000));
        assertFalse(verify("{}", "t=invalid,v1=fff", 1000));
        assertFalse(verify("{}", sign("{}", 1000) + ",t=1000", 1000));
        assertFalse(verify("{}", null, 1000));
        assertFalse(StripeWebhookSignature.verify("{}".getBytes(StandardCharsets.UTF_8), sign("{}", 1000), "", 1000));
    }
}
