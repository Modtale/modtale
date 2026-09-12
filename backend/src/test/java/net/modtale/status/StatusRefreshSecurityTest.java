package net.modtale.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;

class StatusRefreshSecurityTest {
    @Test
    void onlyVerifiedSchedulerIdentityWithTheExactAudienceIsAccepted() {
        StatusServiceProperties properties = new StatusServiceProperties();
        properties.setRefreshAudience("https://status.run.app");
        properties.setRefreshServiceAccount("scheduler@project.iam.gserviceaccount.com");
        StatusWebConfig config = new StatusWebConfig(properties);
        assertFalse(config.validateSchedulerIdentity(token(properties.getRefreshAudience(), properties.getRefreshServiceAccount(), true)).hasErrors());
        assertTrue(config.validateSchedulerIdentity(token("https://other.run.app", properties.getRefreshServiceAccount(), true)).hasErrors());
        assertTrue(config.validateSchedulerIdentity(token(properties.getRefreshAudience(), "other@project.iam.gserviceaccount.com", true)).hasErrors());
        assertTrue(config.validateSchedulerIdentity(token(properties.getRefreshAudience(), properties.getRefreshServiceAccount(), false)).hasErrors());
    }

    private Jwt token(String audience, String email, boolean verified) {
        return Jwt.withTokenValue("test").header("alg", "RS256")
                .audience(List.of(audience)).claim("email", email).claim("email_verified", verified).build();
    }
}
