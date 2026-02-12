package net.modtale.model.resources;

import java.util.UUID;

public class Comment {
    private String id;
    private String user;
    private String userAvatarUrl;
    private String content;
    private String date;
    private String updatedAt;

    private Reply developerReply;

    public Comment() {}

    public Comment(String user, String userAvatarUrl, String content) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.userAvatarUrl = userAvatarUrl;
        this.content = content;
        this.date = java.time.LocalDate.now().toString();
    }

    public static class Reply {
        private String user;
        private String userAvatarUrl;
        private String content;
        private String date;

        public Reply() {}

        public Reply(String user, String userAvatarUrl, String content) {
            this.user = user;
            this.userAvatarUrl = userAvatarUrl;
            this.content = content;
            this.date = java.time.LocalDate.now().toString();
        }

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getUserAvatarUrl() { return userAvatarUrl; }
        public void setUserAvatarUrl(String userAvatarUrl) { this.userAvatarUrl = userAvatarUrl; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getUserAvatarUrl() { return userAvatarUrl; }
    public void setUserAvatarUrl(String userAvatarUrl) { this.userAvatarUrl = userAvatarUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Reply getDeveloperReply() { return developerReply; }
    public void setDeveloperReply(Reply developerReply) { this.developerReply = developerReply; }
}