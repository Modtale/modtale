package net.modtale.launcher.hytale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HytaleAuthSession {

    private String accessToken = "";
    private String refreshToken = "";
    private Instant expiresAt = Instant.EPOCH;
    private String sessionToken = "";
    private String identityToken = "";
    private String username = "";
    private String uuid = "";
    private String accountOwnerId = "";
    private List<HytaleProfile> profiles = new ArrayList<>();

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken == null ? "" : sessionToken;
    }

    public String getIdentityToken() {
        return identityToken;
    }

    public void setIdentityToken(String identityToken) {
        this.identityToken = identityToken == null ? "" : identityToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public String getAccountOwnerId() {
        return accountOwnerId;
    }

    public void setAccountOwnerId(String accountOwnerId) {
        this.accountOwnerId = accountOwnerId == null ? "" : accountOwnerId;
    }

    public List<HytaleProfile> getProfiles() {
        return List.copyOf(profiles);
    }

    public void setProfiles(List<HytaleProfile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean hasLaunchTokens() {
        return identityToken != null && !identityToken.isBlank()
                && sessionToken != null && !sessionToken.isBlank()
                && uuid != null && !uuid.isBlank();
    }

    @Override
    public String toString() {
        return username == null || username.isBlank() ? "Hytale account" : username;
    }
}
