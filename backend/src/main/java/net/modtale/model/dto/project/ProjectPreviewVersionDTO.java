package net.modtale.model.dto.project;

import java.util.List;

public record ProjectPreviewVersionDTO(String versionNumber, List<String> incompatibleProjectIds) {
}
