package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateGalleryImageCaptionRequest {
    @NotBlank(message = "An image URL is required before a gallery image caption can be updated.")
    @Size(max = 2048, message = "Gallery image URLs must be 2,048 characters or fewer.")
    private String imageUrl;

    @Size(max = 240, message = "Gallery image captions must be 240 characters or fewer.")
    private String caption;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
