package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RemoveGalleryImageRequest {
    @NotBlank(message = "An image URL is required before a gallery image can be removed.")
    @Size(max = 2048, message = "Gallery image URLs must be 2,048 characters or fewer.")
    private String imageUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
