package net.modtale.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        @DefaultValue("default-secret-change-in-prod") String preAuthSecret,
        @DefaultValue("600") long preAuthExpirySeconds,
        @DefaultValue("120") long baselineConfidenceDecayDays,
        @DefaultValue("2") long autoApproveDelayMinutesMin,
        @DefaultValue("12") long autoApproveDelayMinutesMax,
        @DefaultValue("15") long knownRiskDelayMinutesMin,
        @DefaultValue("120") long knownRiskDelayMinutesMax,
        @DefaultValue("25") long scanTimeoutMinutes,
        @DefaultValue("2") int scanMaxRetries
) {
}
