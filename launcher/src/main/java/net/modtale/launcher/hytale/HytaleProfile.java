package net.modtale.launcher.hytale;

public record HytaleProfile(String username, String uuid, String owner, long playtimeSeconds) {

    public HytaleProfile(String username, String uuid, String owner) {
        this(username, uuid, owner, 0);
    }

    public HytaleProfile {
        username = username == null ? "" : username;
        uuid = uuid == null ? "" : uuid;
        owner = owner == null ? "" : owner;
        playtimeSeconds = Math.max(0, playtimeSeconds);
    }

    public String displayName() {
        return username.isBlank() ? "Hytale profile" : username;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
