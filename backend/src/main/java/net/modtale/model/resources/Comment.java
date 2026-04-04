package net.modtale.model.resources;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class Comment {
    private String id;
    private String userId;
    private String content;
    private String date;
    private String updatedAt;

    private Set<String> upvotes = new HashSet<>();
    private Set<String> downvotes = new HashSet<>();

    private Reply developerReply;

    public Comment() {}

    public Comment(String userId, String content) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.content = content;
        this.date = java.time.LocalDateTime.now().toString();
    }

    public static class Reply {
        private String userId;
        private String content;
        private String date;
        private Set<String> upvotes = new HashSet<>();
        private Set<String> downvotes = new HashSet<>();

        public Reply() {}

        public Reply(String userId, String content) {
            this.userId = userId;
            this.content = content;
            this.date = java.time.LocalDateTime.now().toString();
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public Set<String> getUpvotes() { return upvotes; }
        public void setUpvotes(Set<String> upvotes) { this.upvotes = upvotes; }
        public Set<String> getDownvotes() { return downvotes; }
        public void setDownvotes(Set<String> downvotes) { this.downvotes = downvotes; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Set<String> getUpvotes() { return upvotes; }
    public void setUpvotes(Set<String> upvotes) { this.upvotes = upvotes; }
    public Set<String> getDownvotes() { return downvotes; }
    public void setDownvotes(Set<String> downvotes) { this.downvotes = downvotes; }
    public Reply getDeveloperReply() { return developerReply; }
    public void setDeveloperReply(Reply developerReply) { this.developerReply = developerReply; }
}