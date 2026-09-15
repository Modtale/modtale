package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Project-scoped discovery and authenticated historical reads; neither activates a held request. */
public final class ProjectMutationReferenceReader {
    public static final String INDEX="project_mutation_project_id";
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    public record Page(List<String> operationIds,String nextCursor) {public Page{operationIds=List.copyOf(operationIds);}}
    public record History(ProjectMutationPreparation.Recovered evidence,ProjectMutationExecutor.Result receipt,ReviewSnapshotArchive.Snapshot applied) {}
    private final MongoCollection<Document> references;
    private final ReviewSnapshotArchive archive;
    private final ProjectMutationPreparation preparation;
    private final ProjectMutationExecutor executor;
    public ProjectMutationReferenceReader(MongoTemplate mongo,ReviewSnapshotArchive archive,ProjectMutationPreparation preparation,ProjectMutationExecutor executor) {
        this.archive=Objects.requireNonNull(archive);this.preparation=Objects.requireNonNull(preparation);this.executor=Objects.requireNonNull(executor);
        references=mongo.getCollection(ProjectMutationExecutor.REFERENCES).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public void initialize() {
        references.withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).createIndex(new Document("projectId",1).append("_id",1),
                new IndexOptions().name(INDEX).collation(BINARY));
    }
    public Page page(Object projectId,String cursor,int limit,BooleanSupplier permitted) {
        permission(permitted);project(projectId);String scope=scope(projectId);
        if(limit<1 || limit>64 || cursor!=null && !cursor.matches("g1\\."+scope+"\\."+UUID))throw invalid();
        var id=new Document("$type","string").append("$regex","^"+UUID+"$");if(cursor!=null)id.append("$gt",cursor.substring(68));
        var query=new Document("projectId",projectId).append("_id",id)
                .append("$expr",new Document("$and",List.of(new Document("$eq",List.of("$projectId",new Document("$literal",projectId))))));
        var rows=ReviewRepairIo.collection(references).find(query).hintString(INDEX).collation(BINARY).sort(new Document("_id",1))
                .projection(new Document("_id",1)).limit(limit+1).batchSize(limit+1).maxTime(5,TimeUnit.SECONDS).into(new ArrayList<>());
        permission(permitted);var ids=rows.stream().limit(limit).map(row->row.getString("_id")).toList();
        return new Page(ids,rows.size()>limit?"g1."+scope+"."+ids.getLast():null);
    }
    public History read(Object projectId,String id,BooleanSupplier permitted) {
        permission(permitted);project(projectId);if(id==null || !id.matches(UUID))throw invalid();
        var reference=ReviewRepairIo.collection(references).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();permission(permitted);
        if(reference==null || !projectId.equals(reference.get("projectId")))throw unavailable();
        var intent=archive.load(id);permission(permitted);
        // Historical attribution selects the record; the current caller's permission remains independently required.
        var recovered=preparation.recover(id,intent.actorId(),permitted);var prepared=recovered.prepared();var before=recovered.before();
        if(!projectId.equals(before.projectId()))throw unavailable();
        var receipt=executor.receipt(prepared,intent.actorId(),permitted);if(!"APPLIED".equals(receipt.state()))throw unavailable();
        if(!(reference.get("afterArchiveId") instanceof String appliedId))throw unavailable();
        var applied=archive.load(appliedId);
        if(!appliedId.equals(java.util.UUID.nameUUIDFromBytes(("project-mutation-applied-1:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())
                || !projectId.equals(applied.projectId()) || applied.versionIndex()!=0 || !intent.actorId().equals(applied.actorId())
                || applied.action()!=ReviewSnapshotArchive.Action.PROJECT_MUTATION_APPLIED || applied.createdAt()!=prepared.createdAt()
                || applied.expiresAt()!=prepared.expiresAt() || !digest(applied.versionBytes()).equals(receipt.afterSha256()))throw unavailable();
        var original=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec());
        var committed=new RawBsonDocument(applied.versionBytes()).decode(new DocumentCodec());
        var versions=committed.getList("versions",Document.class);var changes=new ArrayList<Document>();
        for(var transition:VersionReviewTransition.classify(original.getList("versions",Document.class),versions)) {
            if(Objects.equals(transition.beforeSha256(),transition.afterSha256()) && transition.beforeIndex()==transition.afterIndex())continue;
            var version=transition.afterIndex()<0?null:versions.get(transition.afterIndex());var scan=version==null?null:version.get("scanResult",Document.class);
            String state=version==null?"REMOVED":scan!=null && "MUTATION_HELD".equals(scan.get("scanState"))?"HELD":"RETAINED";
            changes.add(new Document("versionId",transition.versionId()).append("beforeIndex",transition.beforeIndex()).append("afterIndex",transition.afterIndex())
                    .append("beforeSha256",transition.beforeSha256()).append("afterSha256",transition.afterSha256()).append("state",state));
        }
        var expected=new Document("_id",id).append("projectId",projectId).append("actor",intent.actorId())
                .append("beforeArchiveId",prepared.beforeArchiveId()).append("beforeSha256",prepared.beforeSha256())
                .append("afterArchiveId",appliedId).append("afterSha256",receipt.afterSha256()).append("versions",changes);
        if(!Arrays.equals(bytes(expected),bytes(reference)))throw unavailable();
        permission(permitted);return new History(recovered,receipt,applied);
    }
    private static void project(Object id) {
        if(!(id instanceof ObjectId || id instanceof String s && !s.isEmpty() && s.length()<=128
                && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s)))throw invalid();
    }
    private static String scope(Object id){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes(new Document("projectId",id))));}catch(Exception invalid){throw new IllegalArgumentException("Invalid project scope",invalid);}}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Project mutation history access is not permitted");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid mutation history request");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Retained mutation history is unavailable or inconsistent");}
}
