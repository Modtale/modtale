package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectGallery(
        List<String> galleryImages,
        Map<String, String> galleryImageCaptions
) {
    public ProjectGallery {
        galleryImages = galleryImages == null ? List.of() : List.copyOf(galleryImages);
        galleryImageCaptions = galleryImageCaptions == null ? Map.of() : Map.copyOf(galleryImageCaptions);
    }
}
