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
public final class VersionMutationReferenceReader {
    public static final String INDEX="version_mutation_project_id";
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    public record Page(List<String> operationIds,String nextCursor) {public Page{operationIds=List.copyOf(operationIds);}}
    public record History(VersionMutationPreparation.Recovered evidence,VersionMutationExecutor.Result receipt,String state) {}
    private final MongoCollection<Document> references;
    private final ReviewSnapshotArchive archive;
    private final VersionMutationPreparation preparation;
    private final VersionMutationExecutor executor;
    public VersionMutationReferenceReader(MongoTemplate mongo,ReviewSnapshotArchive archive,VersionMutationPreparation preparation,VersionMutationExecutor executor) {
        this.archive=Objects.requireNonNull(archive);this.preparation=Objects.requireNonNull(preparation);this.executor=Objects.requireNonNull(executor);
        references=mongo.getCollection(VersionMutationExecutor.REFERENCES).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public void initialize() {
        references.withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).createIndex(new Document("projectId",1).append("_id",1),
                new IndexOptions().name(INDEX).collation(BINARY));
    }
    public Page page(Object projectId,String cursor,int limit,BooleanSupplier permitted) {
        permission(permitted);project(projectId);String scope=scope(projectId);
        if(limit<1 || limit>64 || cursor!=null && !cursor.matches("m1\\."+scope+"\\."+UUID))throw invalid();
        var id=new Document("$type","string").append("$regex","^"+UUID+"$");if(cursor!=null)id.append("$gt",cursor.substring(68));
        var query=new Document("projectId",projectId).append("_id",id).append("state",new Document("$in",List.of("HELD","REMOVED")))
                .append("$expr",new Document("$and",List.of(new Document("$eq",List.of("$projectId",new Document("$literal",projectId))),
                        new Document("$eq",List.of(new Document("$type","$state"),"string")))));
        var rows=ReviewRepairIo.collection(references).find(query).hintString(INDEX).collation(BINARY).sort(new Document("_id",1))
                .projection(new Document("_id",1)).limit(limit+1).batchSize(limit+1).maxTime(5,TimeUnit.SECONDS).into(new ArrayList<>());
        permission(permitted);var ids=rows.stream().limit(limit).map(row->row.getString("_id")).toList();
        return new Page(ids,rows.size()>limit?"m1."+scope+"."+ids.getLast():null);
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
        String state=prepared.mutation()==VersionMutationPreparation.Mutation.REMOVAL?"REMOVED":"HELD";
        var original=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec());
        var expected=new Document("_id",id).append("actor",intent.actorId()).append("projectId",projectId).append("versionId",original.get("_id"))
                .append("beforeArchiveId",prepared.beforeArchiveId()).append("beforeSha256",prepared.beforeSha256()).append("afterSha256",receipt.afterSha256()).append("state",state);
        if(!Arrays.equals(bytes(expected),bytes(reference)))throw unavailable();
        permission(permitted);return new History(recovered,receipt,state);
    }
    private static void project(Object id) {
        if(!(id instanceof ObjectId || id instanceof String s && !s.isEmpty() && s.length()<=128
                && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s)))throw invalid();
    }
    private static String scope(Object id){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes(new Document("projectId",id))));}catch(Exception invalid){throw new IllegalArgumentException("Invalid project scope",invalid);}}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Version mutation history access is not permitted");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid mutation history request");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Retained mutation history is unavailable or inconsistent");}
}
