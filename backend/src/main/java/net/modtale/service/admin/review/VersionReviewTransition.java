package net.modtale.service.admin.review;

import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import java.security.MessageDigest;
import java.util.*;

/** Classifies captured BSON without granting mutation, dispatch, or approval authority. */
public final class VersionReviewTransition {
    public enum Change { ADDED, REMOVED, ARTIFACT, CONTEXT, REVIEW, METADATA, UNKNOWN }
    public record Transition(String versionId,int beforeIndex,int afterIndex,Set<Change> changes,
                             boolean priorHistory,String beforeSha256,String afterSha256) {
        public Transition { changes=Set.copyOf(changes); }
        public boolean requiresRetention() {
            return beforeIndex>=0 && (changes.contains(Change.UNKNOWN)
                    || priorHistory && changes.stream().anyMatch(change -> change!=Change.METADATA && change!=Change.ADDED));
        }
    }
    private static final Set<String> ARTIFACT=Set.of("fileUrl","hash","manifestId","manifestVersion","overrideFileUrl","modpackConfigs");
    private static final Set<String> CONTEXT=Set.of("gameVersions","dependencies");
    private static final Set<String> REVIEW=Set.of("versionMutation","scanResult","retainedRemoteReview","reviewReplacement","reviewIsolation","replacementSecurityHold",
            "findingReviewHead","approvedFindingReviewHead","reviewStatus","rejectionReason","scheduledPublishDate","securityApprovalProjectId",
            "approvedReviewOrigins","approvedSecurityEvidence","approvedSecurityContextSha256","securityApprovedAt","approvedIssueBaselines");
    private static final Set<String> METADATA=Set.of("versionNumber","changelog","channel","incompatibleProjectIds","downloadCount","releaseDate");
    private VersionReviewTransition() {}

    /** Uses IDs rather than array positions for matching, retaining both positions for the eventual signed proposal. */
    public static List<Transition> classify(List<Document> before,List<Document> after) {
        var left=index(before);var right=index(after);var ids=new LinkedHashSet<>(left.keySet());ids.addAll(right.keySet());
        var result=new ArrayList<Transition>();
        for(var id:ids) {
            Integer oldIndex=left.get(id),newIndex=right.get(id);
            Document old=oldIndex==null?null:before.get(oldIndex),next=newIndex==null?null:after.get(newIndex);
            var changes=EnumSet.noneOf(Change.class);
            if(old==null)changes.add(Change.ADDED);
            else if(next==null)changes.add(Change.REMOVED);
            else {
                var fields=new HashSet<>(old.keySet());fields.addAll(next.keySet());fields.remove("_id");
                for(var field:fields) {
                    Object a=old.get(field),b=next.get(field);
                    boolean known=ARTIFACT.contains(field)||CONTEXT.contains(field)||REVIEW.contains(field)||METADATA.contains(field);
                    if(equal(field,a,b) && (known || old.containsKey(field)==next.containsKey(field)))continue;
                    changes.add(ARTIFACT.contains(field)?Change.ARTIFACT:CONTEXT.contains(field)?Change.CONTEXT:
                            REVIEW.contains(field)?Change.REVIEW:METADATA.contains(field)?Change.METADATA:Change.UNKNOWN);
                }
            }
            result.add(new Transition(id,oldIndex==null?-1:oldIndex,newIndex==null?-1:newIndex,changes,history(old),digest(old),digest(next)));
        }
        return List.copyOf(result);
    }
    private static Map<String,Integer> index(List<Document> versions) {
        if(versions==null)throw new IllegalArgumentException("Missing version collection");
        bytes(new Document("versions",versions));
        var result=new LinkedHashMap<String,Integer>();
        for(int i=0;i<versions.size();i++) {
            var version=versions.get(i);
            if(version==null || !(version.get("_id") instanceof String id) || id.isBlank() || id.length()>128
                    || id.chars().anyMatch(Character::isISOControl) || result.put(id,i)!=null)
                throw new IllegalArgumentException("Ambiguous version identity");
        }
        return result;
    }
    private static boolean history(Document version) {
        if(version==null)return false;
        if(version.get("reviewStatus")!=null && !"PENDING".equals(version.get("reviewStatus")))return true;
        if(version.get("scheduledPublishDate")!=null || version.get("rejectionReason")!=null)return true;
        // Malformed non-null records still count as history; classification must never erase them as absent.
        for(var field:REVIEW) {
            if(Set.of("reviewStatus","rejectionReason","scheduledPublishDate","securityApprovedAt").contains(field))continue;
            if(version.get(field)!=null)return true;
        }
        return version.get("securityApprovedAt")!=null && !Objects.equals(version.get("securityApprovedAt"),0L)
                && !Objects.equals(version.get("securityApprovedAt"),0);
    }
    private static boolean equal(String field,Object left,Object right) {
        if("gameVersions".equals(field) && strings(left) && strings(right))
            return sorted(left).equals(sorted(right));
        if("dependencies".equals(field) && documents(left) && documents(right))
            return sortedDocuments(left).equals(sortedDocuments(right));
        // BSON equality here preserves numeric types. Field order in embedded documents is conservatively significant.
        return Arrays.equals(bytes(new Document("value",left)),bytes(new Document("value",right)));
    }
    private static boolean documents(Object value) {
        return value==null || value instanceof List<?> list && list.stream().allMatch(item -> item instanceof Document);
    }
    private static List<String> sortedDocuments(Object value) {
        var copy=new ArrayList<String>();
        if(value instanceof List<?> list)for(var item:list)copy.add(HexFormat.of().formatHex(bytes((Document)item)));
        Collections.sort(copy);return copy;
    }
    private static boolean strings(Object value) {
        return value==null || value instanceof List<?> list && list.stream().allMatch(item -> item instanceof String);
    }
    private static List<String> sorted(Object value) {
        var copy=new ArrayList<String>();if(value instanceof List<?> list)for(var item:list)copy.add((String)item);
        Collections.sort(copy);return copy;
    }
    private static String digest(Document value) {
        if(value==null)return null;
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static byte[] bytes(Document value) {
        var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();
        if(buffer.remaining()>ReviewSnapshotArchive.MAX_BYTES)throw new IllegalArgumentException("Version transition exceeds snapshot limit");
        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
}
