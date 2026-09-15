package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.*;
import java.util.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.query.*;

/** Flattened approval ancestry; copied approvals never become independent trust roots. */
public final class ArtifactReviewLineage {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final List<String> FIELDS = List.of("_id", "hash", "reviewStatus", "findingReviewHead", "approvedFindingReviewHead",
            "securityApprovalProjectId", "approvedSecurityEvidence", "approvedSecurityContextSha256", "securityApprovedAt", "approvedReviewOrigins",
            "gameVersions", "dependencies", "manifestId", "manifestVersion", "overrideFileUrl", "modpackConfigs");
    private ArtifactReviewLineage() {}

    public static boolean wellFormed(Map<String, String> origins) {
        return origins != null && origins.size() <= 32 && origins.entrySet().stream().allMatch(entry ->
                validId(entry.getKey()) && SecurityManifest.digest(entry.getValue()));
    }
    private static boolean validId(String id) { return id != null && id.matches("[a-zA-Z0-9_-]{1,128}"); }
    public static String stamp(ProjectVersion source) {
        if (source == null || source.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED
                || !Objects.equals(source.getFindingReviewHead(), source.getApprovedFindingReviewHead()) || !wellFormed(source.getApprovedReviewOrigins())) return null;
        var evidence = source.getApprovedSecurityEvidence();
        String context = ArtifactReviewContext.fingerprint(source);
        long now = System.currentTimeMillis();
        if (!validId(source.getId())
                || source.getSecurityApprovalProjectId() == null || source.getSecurityApprovalProjectId().isBlank() || evidence == null || !evidence.complete() || context == null
                || !context.equals(source.getApprovedSecurityContextSha256())
                || !Objects.equals(source.getHash(), evidence.artifactSha256())
                || !SecurityManifest.digest(evidence.contentSha256()) || !SecurityManifest.digest(evidence.artifactSha256())
                || evidence.policyVersion() == null || !evidence.policyVersion().matches("warden-3\\.0\\.0:[0-9a-f]{64}")
                || source.getSecurityApprovedAt() <= 0 || source.getSecurityApprovedAt() > now
                || now - source.getSecurityApprovedAt() > 30L * 86400000) return null;
        try {
            var fields = Arrays.asList("approval-origin-2", source.getFindingReviewHead(), source.getApprovedFindingReviewHead(), source.getSecurityApprovalProjectId(), source.getId(), source.getHash(), evidence.policyVersion(),
                    evidence.contentSha256(), evidence.reviewState(), evidence.clearanceGranted(), context,
                    source.getSecurityApprovedAt(), source.getApprovedReviewOrigins());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(fields)));
        } catch (Exception invalid) { return null; }
    }
    public static boolean valid(Project project, Map<String, String> origins) {
        if (!wellFormed(origins) || project == null || project.getVersions() == null) return false;
        var versions = new HashMap<String, ProjectVersion>();
        for (var version : project.getVersions()) {
            if (version == null || version.getId() == null || versions.put(version.getId(), version) != null) return false;
        }
        for (var origin : origins.entrySet()) {
            var source = versions.get(origin.getKey());
            if (source == null || !Objects.equals(project.getId(), source.getSecurityApprovalProjectId()) || !origin.getValue().equals(stamp(source))) return false;
            // Every ancestor of every copied source must also appear in this flattened proof.
            for (var ancestor : source.getApprovedReviewOrigins().entrySet())
                if (!ancestor.getValue().equals(origins.get(ancestor.getKey()))) return false;
        }
        return true;
    }
    public static Map<String,String> extend(Project project, ProjectVersion source) {
        if (project == null || source == null || !Objects.equals(project.getId(), source.getSecurityApprovalProjectId())) return null;
        String stamp = stamp(source);
        if (stamp == null || !valid(project, source.getApprovedReviewOrigins())) return null;
        var origins = new TreeMap<>(source.getApprovedReviewOrigins());
        if (origins.putIfAbsent(source.getId(), stamp) != null || origins.size() > 32) return null;
        return Map.copyOf(origins);
    }

    public static void invalidate(ScanResult scan) {
        scan.setReusedReviewVersion(null); scan.setReusedReviewApprovedAt(0);
        if (!"BLOCK".equals(scan.getVerdict()) && scan.getStatus() != ScanStatus.INFECTED) {
            scan.setVerdict("REVIEW"); scan.setStatus(ScanStatus.SUSPICIOUS);
        }
        if (scan.getIssues() != null) for (var issue : scan.getIssues()) if (issue != null) {
            issue.setResolved(false); issue.setHistoricalFileEvidenceIdentical(false);
        }
        var notes = new ArrayList<>(scan.getReviewerNotes() == null ? List.<String>of() : scan.getReviewerNotes());
        notes.add("The source approval changed or is unavailable. A current moderator review is required.");
        scan.setReviewerNotes(notes);
    }
    /** Adds source checks to the same atomic write as publication; false never authorizes a write. */
    public static boolean bind(MongoTemplate mongo, String projectId, ScanResult scan, Query query) {
        if (scan.getReusedReviewVersion() == null) return scan.getReusedReviewOrigins() == null || scan.getReusedReviewOrigins().isEmpty();
        var origins = scan.getReusedReviewOrigins();
        if (!wellFormed(origins) || origins.isEmpty()) return false;
        var entity = mongo.getConverter().getMappingContext().getPersistentEntity(Project.class);
        var filter = new QueryMapper(mongo.getConverter()).getMappedObject(new Document("_id", projectId), entity);
        var raw = mongo.getCollection(mongo.getCollectionName(Project.class)).find(filter).first();
        if (raw == null) return false;
        var project = mongo.getConverter().read(Project.class, raw);
        var evidence = scan.getSecurityEvidence();
        if (!valid(project, origins) || evidence == null || !SecurityManifest.digest(scan.getReviewedContextSha256())) return false;
        for (var source : project.getVersions()) if (origins.containsKey(source.getId())) {
            try {
                net.modtale.service.security.issue.FindingReviewHistory.requireManualApproval(mongo, projectId, source);
            } catch (org.springframework.web.server.ResponseStatusException invalidHistory) {
                return false;
            }
            var prior = source.getApprovedSecurityEvidence();
            if (!Objects.equals(evidence.contentSha256(), prior.contentSha256())
                    || !Objects.equals(evidence.policyVersion(), prior.policyVersion())
                    || !Objects.equals(scan.getReviewedContextSha256(), source.getApprovedSecurityContextSha256())
                    || scan.getReusedReviewApprovedAt() != source.getSecurityApprovedAt()) return false;
        }
        var checks = new ArrayList<Document>();
        for (var item : raw.getList("versions", Document.class)) {
            if (!origins.containsKey(Objects.toString(item.get("_id"), null))) continue;
            var terms = new ArrayList<Document>();
            for (String field : FIELDS) {
                var actual = new Document("$ifNull", Arrays.asList("$$origin." + field, null));
                terms.add(new Document("$eq", Arrays.asList(actual, new Document("$literal", item.get(field)))));
            }
            var databaseNow = new Document("$toLong", "$$NOW");
            terms.add(new Document("$lte", List.of("$$origin.securityApprovedAt", databaseNow)));
            terms.add(new Document("$gt", List.of("$$origin.securityApprovedAt",
                    new Document("$subtract", List.of(databaseNow, 30L * 86400000)))));
            var matches = new Document("$filter", new Document("input", "$versions").append("as", "origin")
                    .append("cond", new Document("$and", terms)));
            checks.add(new Document("$eq", List.of(new Document("$size", matches), 1)));
        }
        if (checks.size() != origins.size()) return false;
        // Expressions inspect source versions without competing with the target's positional match.
        query.addCriteria(Criteria.where("$expr").is(new Document("$and", checks)));
        return true;
    }
}
