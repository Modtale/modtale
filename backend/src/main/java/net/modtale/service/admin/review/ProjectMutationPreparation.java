package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One immutable proposal for a grouped project mutation; no child proposal can be executed independently. */
public final class ProjectMutationPreparation {
    public enum Mutation { VERSION_LIST, SUBMISSION }
    public record Captured(Object projectId,byte[] bytes,String sha256) {
        public Captured{bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
        @Override public String toString(){return "CapturedProjectMutation["+sha256+"]";}
    }
    public record Request(String id,Object projectId,String expectedSha256,String actor,Mutation mutation,byte[] proposedProject) {
        public Request{proposedProject=proposedProject==null?null:proposedProject.clone();}
        @Override public byte[] proposedProject(){return proposedProject==null?null:proposedProject.clone();}
        @Override public String toString(){return "ProjectMutationRequest["+id+"]";}
    }
    public record Prepared(String id,String beforeArchiveId,String afterArchiveId,String beforeSha256,String afterSha256,
                           Mutation mutation,long createdAt,long expiresAt) {}
    public record Recovered(Prepared prepared,ReviewSnapshotArchive.Snapshot before,ReviewSnapshotArchive.Snapshot after,
                            List<VersionReviewTransition.Transition> transitions) {public Recovered{transitions=List.copyOf(transitions);}}
    private static final Set<String> LIST_FIELDS=Set.of("versions","childProjectIds","classification","updatedAt");
    private static final Set<String> SUBMIT_FIELDS=Set.of("versions","status","expiresAt","updatedAt");
    private final MongoCollection<RawBsonDocument> projects;private final ReviewSnapshotArchive archive;private final Clock clock;private final long lifetime;
    public ProjectMutationPreparation(MongoTemplate mongo,ReviewSnapshotArchive archive,Clock clock,long lifetime) {
        if(lifetime<1000 || lifetime>120000)throw new IllegalArgumentException("Invalid grouped mutation lifetime");
        this.archive=Objects.requireNonNull(archive);this.clock=Objects.requireNonNull(clock);this.lifetime=lifetime;
        projects=mongo.getCollection("projects").withDocumentClass(RawBsonDocument.class).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Captured capture(Object projectId,BooleanSupplier permitted) {
        permission(permitted);project(projectId);
        var raw=ReviewRepairIo.collection(projects).find(new Document("_id",projectId)).collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();permission(permitted);
        if(raw==null)throw invalid();var buffer=raw.getByteBuffer().asNIO();byte[] data=new byte[buffer.remaining()];buffer.get(data);
        var decoded=decode(data);if(!projectId.equals(decoded.get("_id")))throw invalid();versions(decoded);
        return new Captured(projectId,data,digest(data));
    }
    public Prepared prepare(Request request,BooleanSupplier permitted) {
        permission(permitted);Objects.requireNonNull(request);
        if(!uuid(request.id()) || request.actor()==null || request.actor().isBlank() || request.actor().length()>256 || request.mutation()==null)throw invalid();
        var captured=capture(request.projectId(),permitted);if(!captured.sha256().equals(request.expectedSha256()))throw invalid();
        validate(captured.bytes(),request.proposedProject(),request.projectId(),request.mutation());
        String beforeId=derived(request.id(),"before"),afterId=derived(request.id(),"after");
        var existing=archive.find(beforeId);permission(permitted);
        long created=existing==null?clock.millis():existing.createdAt(),expires=existing==null?Math.addExact(created,lifetime):existing.expiresAt();live(created,expires);
        retain(new ReviewSnapshotArchive.Snapshot(beforeId,request.projectId(),0,request.actor(),ReviewSnapshotArchive.Action.PROJECT_MUTATION_BEFORE,created,expires,captured.bytes()),permitted);
        retain(new ReviewSnapshotArchive.Snapshot(afterId,request.projectId(),0,request.actor(),ReviewSnapshotArchive.Action.PROJECT_MUTATION_AFTER,created,expires,request.proposedProject()),permitted);
        var payload=new Document("schema",1).append("beforeArchiveId",beforeId).append("afterArchiveId",afterId).append("beforeSha256",captured.sha256())
                .append("afterSha256",digest(request.proposedProject())).append("mutation",request.mutation().name());
        retain(new ReviewSnapshotArchive.Snapshot(request.id(),request.projectId(),0,request.actor(),ReviewSnapshotArchive.Action.PROJECT_MUTATION_INTENT,created,expires,bytes(payload)),permitted);
        if(!Arrays.equals(captured.bytes(),capture(request.projectId(),permitted).bytes()))throw invalid();
        var result=recover(request.id(),request.actor(),permitted).prepared();live(result.createdAt(),result.expiresAt());return result;
    }
    public Recovered recover(String id,String actor,BooleanSupplier permitted) {
        permission(permitted);var intent=archive.load(id);permission(permitted);
        if(intent.action()!=ReviewSnapshotArchive.Action.PROJECT_MUTATION_INTENT || !intent.actorId().equals(actor) || intent.versionIndex()!=0
                || intent.expiresAt()-intent.createdAt()>120000)throw invalid();
        var payload=decode(intent.versionBytes());if(payload.size()!=6 || !Integer.valueOf(1).equals(payload.get("schema")))throw invalid();
        var mutation=Mutation.valueOf(payload.getString("mutation"));String beforeId=derived(id,"before"),afterId=derived(id,"after");
        if(!beforeId.equals(payload.get("beforeArchiveId")) || !afterId.equals(payload.get("afterArchiveId")))throw invalid();
        var before=archive.load(beforeId);permission(permitted);var after=archive.load(afterId);permission(permitted);
        header(intent,before,ReviewSnapshotArchive.Action.PROJECT_MUTATION_BEFORE);header(intent,after,ReviewSnapshotArchive.Action.PROJECT_MUTATION_AFTER);
        if(!digest(before.versionBytes()).equals(payload.get("beforeSha256")) || !digest(after.versionBytes()).equals(payload.get("afterSha256")))throw invalid();
        var transitions=validate(before.versionBytes(),after.versionBytes(),intent.projectId(),mutation);permission(permitted);
        return new Recovered(new Prepared(id,beforeId,afterId,payload.getString("beforeSha256"),payload.getString("afterSha256"),mutation,intent.createdAt(),intent.expiresAt()),before,after,transitions);
    }
    public static Set<net.modtale.model.user.ApiKey.ApiPermission> requiredPermissions(byte[] beforeBytes,byte[] afterBytes,Object projectId,Mutation mutation) {
        Objects.requireNonNull(mutation);var transitions=validate(beforeBytes,afterBytes,projectId,mutation);
        var permissions=EnumSet.noneOf(net.modtale.model.user.ApiKey.ApiPermission.class);
        if(mutation==Mutation.SUBMISSION)permissions.add(net.modtale.model.user.ApiKey.ApiPermission.PROJECT_STATUS_SUBMIT);
        boolean membershipChanged=false,moved=false;
        for(var transition:transitions) {
            if(transition.beforeIndex()<0){permissions.add(net.modtale.model.user.ApiKey.ApiPermission.VERSION_CREATE);membershipChanged=true;}
            else if(transition.afterIndex()<0){permissions.add(net.modtale.model.user.ApiKey.ApiPermission.VERSION_DELETE);membershipChanged=true;}
            else {
                moved|=transition.beforeIndex()!=transition.afterIndex();
                if(transition.changes().stream().anyMatch(change->mutation!=Mutation.SUBMISSION || change!=VersionReviewTransition.Change.REVIEW))
                    permissions.add(net.modtale.model.user.ApiKey.ApiPermission.VERSION_EDIT);
            }
        }
        var before=decode(beforeBytes);var after=decode(afterBytes);
        if(moved && !membershipChanged || !Arrays.equals(bytes(new Document("v",before.get("childProjectIds"))),bytes(new Document("v",after.get("childProjectIds")))))
            permissions.add(net.modtale.model.user.ApiKey.ApiPermission.VERSION_EDIT);
        if(permissions.isEmpty())throw invalid();return Set.copyOf(permissions);
    }
    private static List<VersionReviewTransition.Transition> validate(byte[] beforeBytes,byte[] afterBytes,Object projectId,Mutation mutation) {
        var before=decode(beforeBytes);var after=decode(afterBytes);if(!projectId.equals(before.get("_id")) || !projectId.equals(after.get("_id")))throw invalid();
        var allowed=mutation==Mutation.VERSION_LIST?LIST_FIELDS:SUBMIT_FIELDS;var fields=new HashSet<>(before.keySet());fields.addAll(after.keySet());
        for(var field:fields)if(!allowed.contains(field) && (before.containsKey(field)!=after.containsKey(field)
                || !Arrays.equals(bytes(new Document("value",before.get(field))),bytes(new Document("value",after.get(field))))))throw invalid();
        if(mutation==Mutation.SUBMISSION && (!"DRAFT".equals(before.get("status")) || !"PENDING".equals(after.get("status"))))throw invalid();
        var transitions=VersionReviewTransition.classify(versions(before),versions(after));
        if(mutation==Mutation.SUBMISSION && transitions.stream().anyMatch(t->t.beforeIndex()<0 || t.afterIndex()<0 || t.beforeIndex()!=t.afterIndex()))throw invalid();
        return transitions;
    }
    private static List<Document> versions(Document project) {
        if(!(project.get("versions") instanceof List<?> values) || values.stream().anyMatch(value->!(value instanceof Document)))throw invalid();
        var versions=values.stream().map(value->(Document)value).toList();VersionReviewTransition.classify(versions,versions);return versions;
    }
    private void retain(ReviewSnapshotArchive.Snapshot candidate,BooleanSupplier permitted) {
        permission(permitted);
        try{archive.retain(candidate);}catch(RuntimeException uncertain){var found=archive.find(candidate.id());if(found==null || !same(candidate,found))throw uncertain;}
        permission(permitted);
    }
    private static boolean same(ReviewSnapshotArchive.Snapshot a,ReviewSnapshotArchive.Snapshot b){return a.id().equals(b.id()) && a.projectId().equals(b.projectId()) && a.versionIndex()==b.versionIndex() && a.actorId().equals(b.actorId()) && a.action()==b.action() && a.createdAt()==b.createdAt() && a.expiresAt()==b.expiresAt() && Arrays.equals(a.versionBytes(),b.versionBytes());}
    private static void header(ReviewSnapshotArchive.Snapshot intent,ReviewSnapshotArchive.Snapshot part,ReviewSnapshotArchive.Action action){if(part.action()!=action || part.versionIndex()!=0 || !part.projectId().equals(intent.projectId()) || !part.actorId().equals(intent.actorId()) || part.createdAt()!=intent.createdAt() || part.expiresAt()!=intent.expiresAt())throw invalid();}
    private static Document decode(byte[] value){if(value==null || value.length<5 || value.length>ReviewSnapshotArchive.MAX_BYTES)throw invalid();var doc=new RawBsonDocument(value).decode(new DocumentCodec());if(!Arrays.equals(value,bytes(doc)))throw invalid();return doc;}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();byte[] data=new byte[buffer.remaining()];buffer.get(data);return data;}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String derived(String id,String phase){return UUID.nameUUIDFromBytes(("project-mutation-1:"+id+":"+phase).getBytes(StandardCharsets.UTF_8)).toString();}
    private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void project(Object id){if(!(id instanceof ObjectId || id instanceof String s && !s.isEmpty() && s.length()<=128 && StandardCharsets.UTF_8.newEncoder().canEncode(s)))throw invalid();}
    private void live(long created,long expires){long now=clock.millis();if(now<created || now>=expires)throw invalid();}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Grouped mutation access is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Grouped mutation evidence is unavailable or inconsistent");}
}
