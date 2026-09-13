package net.modtale.service.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoFactorServiceTest {
    @Test void currentAuthenticatorCodeStillVerifies() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        String code = new dev.samstevens.totp.code.DefaultCodeGenerator()
                .generate(secret, System.currentTimeMillis() / 30000);
        assertTrue(new TwoFactorService().isOtpValid(secret, code));
    }

    @Test void missingSecretsAndMalformedCodesCannotAuthenticate() {
        var service = new TwoFactorService();
        for (String secret : new String[]{null, "", "   "}) {
            assertFalse(service.isOtpValid(secret, "000000"));
        }
        for (String code : new String[]{null, "", "12345", "1234567", "abcdef", "１２３４５６", "123456\n"}) {
            assertFalse(service.isOtpValid("JBSWY3DPEHPK3PXP", code));
        }
    }
}
