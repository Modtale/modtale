package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded held-request discovery. Candidates require authenticated admission preparation. */
public final class ProjectMutationDiscovery {
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public record Cursor(Object projectId,int offset,boolean afterProject) {
        public Cursor {project(projectId);if(offset<0 || offset>ReviewSnapshotArchive.MAX_BYTES || afterProject && offset!=0)throw invalid();}
    }
    public record Candidate(Object projectId,int versionIndex,String versionId,String mutationId,String requestId,int attempt) {}
    public record Page(List<Candidate> candidates,Cursor next,int examined,int unavailable) {
        public Page {candidates=List.copyOf(candidates);}
    }
    private final MongoCollection<Document> projects;
    public ProjectMutationDiscovery(MongoTemplate mongo) {
        projects=mongo.getCollection("projects").withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Page page(Cursor cursor,int limit) {
        if(limit<1 || limit>64)throw invalid();
        var terms=new ArrayList<Object>();terms.add(boundedString("$_id",128,true));terms.add(new Document("$eq",List.of(new Document("$type","$status"),"string")));
        if(cursor!=null)terms.add(new Document(cursor.afterProject()?"$gt":"$gte",List.of("$_id",literal(cursor.projectId()))));
        var match=new Document("_id",new Document("$type",List.of("string","objectId"))).append("status",new Document("$in",List.of("PENDING","PUBLISHED","UNLISTED","PRIVATE")))
                .append("versions",new Document("$elemMatch",new Document("scanResult.scanState","MUTATION_HELD"))).append("$expr",new Document("$and",terms));
        Object offset=cursor==null?literal(0):new Document("$cond",List.of(new Document("$eq",List.of("$_id",literal(cursor.projectId()))),cursor.offset(),0));
        var array=new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of()));
        var view=new Document("held",new Document("$eq",List.of("$$v.scanResult.scanState","MUTATION_HELD")))
                .append("pending",new Document("$and",List.of(new Document("$eq",List.of("$$v.reviewStatus","PENDING")),new Document("$eq",List.of("$$v.scanResult.status","SCANNING")),new Document("$eq",List.of("$$v.scanResult.manualRescan",false)))))
                .append("versionId",safeString("$$v._id",128)).append("mutationId",safeString("$$v.versionMutation.operationId",36))
                .append("requestId",safeString("$$v.scanResult.scanRequestId",36)).append("pointerRequest",safeString("$$v.versionMutation.requestId",36))
                .append("beforeSha256",safeString("$$v.versionMutation.beforeSha256",64))
                .append("pointerSize",new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type","$$v.versionMutation"),"object")),new Document("$size",new Document("$objectToArray","$$v.versionMutation")),-1)))
                .append("attempt",new Document("$cond",Arrays.asList(new Document("$in",List.of(new Document("$type","$$v.scanResult.scanAttempt"),List.of("int","long"))),"$$v.scanResult.scanAttempt",null)));
        var projection=new Document("_id",1).append("offset",offset).append("total",new Document("$size",array))
                .append("items",new Document("$map",new Document("input",new Document("$slice",List.of(array,offset,limit))).append("as","v").append("in",view)));
        var pipeline=List.of(new Document("$match",match),new Document("$sort",new Document("_id",1)),new Document("$limit",1),new Document("$project",projection));
        Document root;
        try(var rows=ReviewRepairIo.collection(projects).aggregate(pipeline).collation(Collation.builder().locale("simple").build()).allowDiskUse(false)
                .maxTime(5,TimeUnit.SECONDS).batchSize(1).iterator()){root=rows.hasNext()?rows.next():null;}
        if(root==null)return new Page(List.of(),null,0,0);
        Object id=root.get("_id");project(id);int start=root.getInteger("offset");var items=root.getList("items",Document.class);var candidates=new ArrayList<Candidate>();int unavailable=0;
        for(int i=0;i<items.size();i++) {
            var item=items.get(i);if(!Boolean.TRUE.equals(item.get("held")))continue;
            String version=item.getString("versionId"),mutation=item.getString("mutationId"),request=item.getString("requestId"),sha=item.getString("beforeSha256");Object attempt=item.get("attempt");
            if(id instanceof String text && text.length()>128 || !Boolean.TRUE.equals(item.get("pending")) || !Integer.valueOf(3).equals(item.get("pointerSize"))
                    || version==null || version.isBlank() || version.length()>128 || version.chars().anyMatch(Character::isISOControl)
                    || mutation==null || !mutation.matches(UUID) || request==null || !request.matches(UUID) || !request.equals(item.get("pointerRequest"))
                    || sha==null || !sha.matches("[0-9a-f]{64}") || !(attempt instanceof Number number) || number.longValue()<1 || number.longValue()>Integer.MAX_VALUE) {unavailable++;continue;}
            candidates.add(new Candidate(id,Math.addExact(start,i),version,mutation,request,((Number)attempt).intValue()));
        }
        int end=Math.addExact(start,items.size());var next=end<root.getInteger("total")?new Cursor(id,end,false):new Cursor(id,0,true);
        return new Page(candidates,next,items.size(),unavailable);
    }
    private static Document safeString(String field,int max){return new Document("$cond",Arrays.asList(boundedString(field,max,false),field,null));}
    private static Document boundedString(String field,int max,boolean objectIdAllowed) {
        return new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type",field),"string")),
                new Document("$and",List.of(new Document("$gt",List.of(new Document("$strLenCP",field),0)),new Document("$lte",List.of(new Document("$strLenCP",field),max)))),
                objectIdAllowed?new Document("$eq",List.of(new Document("$type",field),"objectId")):false));
    }
    private static void project(Object id){if(!(id instanceof ObjectId || id instanceof String s && !s.isEmpty() && s.codePointCount(0,s.length())<=128 && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(s)))throw invalid();}
    private static Document literal(Object value){return new Document("$literal",value);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid held request discovery scope");}
}
