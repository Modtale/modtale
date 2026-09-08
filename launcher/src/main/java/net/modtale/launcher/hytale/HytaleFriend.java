package net.modtale.launcher.hytale;

public record HytaleFriend(String username, String uuid, String status, String avatarUrl, boolean online) {

    public HytaleFriend {
        username = username == null ? "" : username.trim();
        uuid = uuid == null ? "" : uuid.trim();
        status = status == null ? "" : status.trim();
        avatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
    }

    public String displayName() {
        if (!username.isBlank()) {
            return username;
        }
        return uuid.isBlank() ? "Hytale friend" : uuid;
    }

    public HytaleFriend withUsername(String username) {
        return new HytaleFriend(username, uuid, status, avatarUrl, online);
    }

    public String displayStatus() {
        if (!status.isBlank()) {
            return status;
        }
        return online ? "Online" : "Offline";
    }
}
