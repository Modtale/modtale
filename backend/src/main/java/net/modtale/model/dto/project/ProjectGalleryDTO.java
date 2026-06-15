package net.modtale.model.dto.project;

import java.util.List;
import java.util.Map;

public record ProjectGalleryDTO(List<String> galleryImages, Map<String, String> galleryImageCaptions) {
}
