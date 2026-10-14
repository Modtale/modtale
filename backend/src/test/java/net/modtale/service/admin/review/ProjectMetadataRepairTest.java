package net.modtale.service.admin.review;

import java.util.*;
import net.modtale.service.admin.project.ProjectMetadataRepair;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class ProjectMetadataRepairTest {
    @Test void acceptsMetadataRepairsWithoutAcceptingAuthorityFields() {
        assertDoesNotThrow(() -> ProjectMetadataRepair.validate(Map.of("title", "Repaired title", "tags", List.of("tools"),
                "links", Map.of("source", "https://example.com"), "allowComments", true)));
        for (String field : List.of("versions", "status", "approvedBy", "authorId", "projectRoles", "teamMembers",
                "classification", "childProjectIds", "reviewToken", "securityApprovedAt", "versions.0.reviewStatus", "$set"))
            assertThrows(ResponseStatusException.class, () -> ProjectMetadataRepair.validate(Map.of(field, "forged")), field);
    }
    @Test void rejectsMalformedAndOversizedMetadata() {
        for (Map<String, Object> value : List.<Map<String, Object>>of(Map.of(), Map.of("title", " "),
                Map.of("allowComments", "true"), Map.of("tags", List.of(Map.of("status", "APPROVED"))),
                Map.of("links", Map.of("source", List.of("bad"))), Map.of("description", "x".repeat(100_001)),
                Map.of("tags", Collections.nCopies(1001, "x")), Map.of("tags", Collections.nCopies(100, "x".repeat(4096)))))
            assertThrows(ResponseStatusException.class, () -> ProjectMetadataRepair.validate(value));
    }
}
