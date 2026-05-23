package net.modtale.model.dto.request.user;

public class UpdateProfileRequest {
    private String bio;
    private String username;

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
