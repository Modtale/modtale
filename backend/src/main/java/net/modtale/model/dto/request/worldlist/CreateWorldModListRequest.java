package net.modtale.model.dto.request.worldlist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;

public record CreateWorldModListRequest(
        @Size(max = 120) String title,
        @Size(max = 120) String worldName,
        @Size(max = 60) String gameVersion,
        @NotEmpty @Size(max = 200) List<@Valid Item> mods
) {
    public record Item(
            @Size(max = 160) String modId,
            @Size(max = 120) String projectId,
            @Size(max = 160) String slug,
            @Size(max = 180) String title,
            @Size(max = 80) String versionNumber,
            ProjectClassification classification,
            ProjectDependency.Source source,
            @Size(max = 180) String externalId,
            @Size(max = 600) String externalUrl,
            @Size(max = 600) String icon
    ) {}
}
