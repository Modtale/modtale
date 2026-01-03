package net.modtale.model.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "api_keys")
public class ApiKey {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String keyHash;
    private String prefix;

    private Tier tier;

    private LocalDateTime lastUsed;
    private LocalDateTime createdAt;

    public enum Tier {
        USER,
        ENTERPRISE
    }

    public ApiKey() {}

    public ApiKey(String userId, String name, String keyHash, String prefix) {
        this.userId = userId;
        this.name = name;
        this.keyHash = keyHash;
        this.prefix = prefix;
        this.tier = Tier.USER;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public Tier getTier() { return tier != null ? tier : Tier.USER; }
    public void setTier(Tier tier) { this.tier = tier; }

    public LocalDateTime getLastUsed() { return lastUsed; }
    public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}