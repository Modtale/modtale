package net.modtale.service.admin.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.modtale.model.project.ProjectVersion;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.util.*;

public final class VersionReviewSnapshot {
    private static final ObjectMapper MAPPER=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final ObjectMapper RESCAN_MAPPER = MAPPER.copy().addMixIn(
            net.modtale.model.project.ScanResult.SecurityEvidence.class, RescanEvidence.class);
    private abstract static class RescanEvidence {
        @com.fasterxml.jackson.annotation.JsonIgnore abstract Map<String, String> entryHashes();
    }
    private VersionReviewSnapshot() {}
    public static String token(ProjectVersion version) {
        return token(version, MAPPER);
    }
    // Rescan requests need not load the old manifest; approval still uses the full evidence snapshot.
    public static String rescanToken(ProjectVersion version) {
        return token(version, RESCAN_MAPPER);
    }
    private static String token(ProjectVersion version, ObjectMapper mapper) {
        if(version==null) throw new IllegalArgumentException("Missing review version");
        try {
            @SuppressWarnings("unchecked") Map<String,Object> fields=mapper.convertValue(version,Map.class);
            fields.remove("downloadCount");
            var scan=version.getScanResult();
            fields.put("verifiedArtifact",scan!=null && scan.isArtifactVerified());
            fields.put("reusedOrigins",scan==null ? null : scan.getReusedReviewOrigins());
            fields.put("reviewedContext",scan==null ? null : scan.getReviewedContextSha256());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(fields)));
        } catch(Exception invalid) {throw new IllegalStateException("Could not bind the review snapshot",invalid);}
    }
    public static void requireCurrent(ProjectVersion version,String expected) {
        if(expected==null || !expected.equals(token(version)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This version changed after the review was opened. Refresh its evidence before deciding.");
    }
}
