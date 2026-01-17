package net.modtale.model.resources;

public class Review {
    private String id;
    private String user;
    private String userAvatarUrl;
    private String comment;
    private int rating;
    private String date;
    private String updatedAt;
    private String developerReply;
    private String developerReplyDate;

    public Review() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getUserAvatarUrl() { return userAvatarUrl; }
    public void setUserAvatarUrl(String userAvatarUrl) { this.userAvatarUrl = userAvatarUrl; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getDeveloperReply() { return developerReply; }
    public void setDeveloperReply(String developerReply) { this.developerReply = developerReply; }

    public String getDeveloperReplyDate() { return developerReplyDate; }
    public void setDeveloperReplyDate(String developerReplyDate) { this.developerReplyDate = developerReplyDate; }
}