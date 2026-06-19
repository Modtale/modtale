package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectClassification;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectMarqueeDTO(
        String id,
        String slug,
        String title,
        String authorId,
        String author,
        String imageUrl,
        String bannerUrl,
        ProjectClassification classification,
        int downloadCount
) {
}
