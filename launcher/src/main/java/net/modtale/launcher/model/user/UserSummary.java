package net.modtale.launcher.model.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(
        String id,
        String username,
        String avatarUrl,
        String bannerUrl,
        String bio,
        String createdAt,
        String tier,
        List<String> roles,
        String accountType,
        List<String> badges
) {
    public UserSummary {
        roles = roles == null ? List.of() : List.copyOf(roles);
        badges = badges == null ? List.of() : List.copyOf(badges);
    }

    @Override
    public String toString() {
        return username == null || username.isBlank() ? id : username;
    }
}
