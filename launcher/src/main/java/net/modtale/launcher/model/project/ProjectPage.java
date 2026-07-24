package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectPage(
        List<ProjectSummary> content,
        int totalPages,
        long totalElements,
        int number,
        boolean last
) {
    public ProjectPage {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
