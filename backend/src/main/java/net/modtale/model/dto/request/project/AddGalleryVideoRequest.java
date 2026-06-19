package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddGalleryVideoRequest {
    @NotBlank(message = "A YouTube URL is required before a gallery video can be added.")
    @Size(max = 2048, message = "Gallery video URLs must be 2,048 characters or fewer.")
    private String videoUrl;

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
