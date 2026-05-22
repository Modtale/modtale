package net.modtale.model.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSummaryDTO(
        String id,
        String username,
        String avatarUrl,
        String bannerUrl,
        String bio,
        String createdAt,
        ApiKey.Tier tier,
        List<String> roles,
        User.AccountType accountType,
        List<String> badges
) {}
