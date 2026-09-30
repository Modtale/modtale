package net.modtale.service.finance;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Verifies the unchanged request body and accepts multiple v1 signatures during secret rotation. */
public final class StripeWebhookSignature {
    private static final long TOLERANCE_SECONDS = 300;
    private StripeWebhookSignature() {}

    public static boolean verify(byte[] body, String signatureHeader, String secret, long nowSeconds) {
        if (body == null || signatureHeader == null || secret == null || secret.isBlank()) return false;
        String timestamp = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                if (timestamp != null) return false;
                timestamp = pair[1];
            }
        }
        if (timestamp == null) return false;
        try {
            long signedAt = Long.parseLong(timestamp);
            if (signedAt < nowSeconds - TOLERANCE_SECONDS || signedAt > nowSeconds + TOLERANCE_SECONDS) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            for (String part : signatureHeader.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2 || !pair[0].equals("v1") || pair[1].length() != 64) continue;
                try {
                    if (MessageDigest.isEqual(expected, HexFormat.of().parseHex(pair[1]))) return true;
                } catch (IllegalArgumentException malformedSignature) {
                    // Continue: Stripe can send more than one signature while rotating a secret.
                }
            }
            return false;
        } catch (NumberFormatException | GeneralSecurityException invalid) {
            return false;
        }
    }
}
