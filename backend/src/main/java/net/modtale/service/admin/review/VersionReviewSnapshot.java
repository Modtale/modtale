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
    private VersionReviewSnapshot() {}
    public static String token(ProjectVersion version) {
        if(version==null) throw new IllegalArgumentException("Missing review version");
        try {
            @SuppressWarnings("unchecked") Map<String,Object> fields=MAPPER.convertValue(version,Map.class);
            fields.remove("downloadCount");
            var scan=version.getScanResult();
            fields.put("verifiedArtifact",scan!=null && scan.isArtifactVerified());
            fields.put("reviewedContext",scan==null ? null : scan.getReviewedContextSha256());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(fields)));
        } catch(Exception invalid) {throw new IllegalStateException("Could not bind the review snapshot",invalid);}
    }
    public static void requireCurrent(ProjectVersion version,String expected) {
        if(expected==null || !expected.equals(token(version)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This version changed after the review was opened. Refresh its evidence before deciding.");
    }
}
