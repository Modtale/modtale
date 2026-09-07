package net.modtale.model.dto.request.project;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ReorderGalleryRequest {

    @NotNull
    private List<String> imageUrls;

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
}
