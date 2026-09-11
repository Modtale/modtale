package net.modtale.service.admin.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.modtale.model.project.Project;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.util.*;

public final class ProjectReviewSnapshot {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private ProjectReviewSnapshot() {}
    public static String token(Project project) {
        try {
            @SuppressWarnings("unchecked") Map<String,Object> fields = MAPPER.convertValue(project, Map.class);
            for (String operational : List.of("downloadCount", "favoriteCount", "downloads7d", "downloads30d", "downloads90d",
                    "trendScore", "relevanceScore", "popularScore", "trendingRank", "popularRank", "relevanceRank",
                    "rankingDirty", "lastTrendingNotification", "comments", "canEdit", "owner")) fields.remove(operational);
            fields.put("versions", project.getVersions() == null ? List.of()
                    : project.getVersions().stream().map(VersionReviewSnapshot::token).toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(fields)));
        } catch (Exception invalid) { throw new IllegalStateException("Could not bind the project review", invalid); }
    }
    public static void requireCurrent(Project project, String expected) {
        if (expected == null || !expected.equals(token(project))) throw conflict();
    }
    public static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "This project changed after the review was opened. Refresh its evidence before deciding.");
    }
}
