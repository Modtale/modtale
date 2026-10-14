package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.Project;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public final class RemoteReviewDiscovery {
    public record Cursor(Object projectId,int offset,boolean afterProject) {
        public Cursor {
            if(!(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.codePointCount(0,s.length())<=128)
                    || offset<0 || offset>16*1024*1024 || afterProject && offset!=0)throw new IllegalArgumentException("Invalid discovery cursor");
        }
    }
    public record Candidate(String projectId,String versionId,int attempt,String requestId) {}
    public record Page(List<Candidate> candidates,Cursor next,int examined) { public Page { candidates=List.copyOf(candidates); } }
    private final MongoCollection<Document> projects;
    public RemoteReviewDiscovery(MongoTemplate mongo) {
        projects=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Page page(Cursor cursor,int limit) {
        if(limit<1 || limit>64)throw new IllegalArgumentException("Invalid discovery page size");
        var eligibility=new Document("reviewStatus","PENDING").append("scanResult.status","SCANNING")
                .append("scanResult.scanState",new Document("$in",List.of("QUEUED","SCANNING","REMOTE_REVIEW")));
        var terms=new ArrayList<Object>();terms.add(boundedString("$_id",128,true));
        if(cursor!=null)terms.add(new Document(cursor.afterProject()?"$gt":"$gte",List.of("$_id",literal(cursor.projectId()))));
        var match=new Document("_id",new Document("$type",List.of("string","objectId"))).append("versions",new Document("$elemMatch",eligibility))
                .append("$expr",new Document("$and",terms));
        Object offset=cursor==null?literal(0):new Document("$cond",List.of(new Document("$eq",List.of("$_id",literal(cursor.projectId()))),cursor.offset(),0));
        var array=new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of()));
        var view=new Document("versionId",safeString("$$v._id",128)).append("requestId",safeString("$$v.scanResult.scanRequestId",36))
                .append("attempt",new Document("$cond",Arrays.asList(new Document("$in",List.of(new Document("$type","$$v.scanResult.scanAttempt"),List.of("int","long"))),"$$v.scanResult.scanAttempt",null)))
                .append("eligible",eligible());
        var projection=new Document("_id",1).append("offset",offset).append("total",new Document("$size",array))
                .append("items",new Document("$map",new Document("input",new Document("$slice",List.of(array,offset,limit))).append("as","v").append("in",view)));
        var pipeline=List.of(new Document("$match",match),new Document("$sort",new Document("_id",1)),new Document("$limit",1),new Document("$project",projection));
        Document root;
        try(var rows=projects.aggregate(pipeline).collation(Collation.builder().locale("simple").build()).allowDiskUse(false)
                .maxTime(5,TimeUnit.SECONDS).batchSize(1).iterator()) {root=rows.hasNext()?rows.next():null;}
        if(root==null)return new Page(List.of(),null,0);
        Object id=root.get("_id");String projectId=id instanceof ObjectId objectId?objectId.toHexString():(String)id;
        var items=root.getList("items",Document.class);var candidates=new ArrayList<Candidate>();
        for(var item:items) {
            if(!Boolean.TRUE.equals(item.get("eligible")))continue;
            String version=item.getString("versionId"),request=item.getString("requestId");Object attempt=item.get("attempt");
            if(version==null || version.isBlank() || version.chars().anyMatch(Character::isISOControl) || request==null
                    || !request.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                    || !(attempt instanceof Number number) || number.longValue()<1 || number.longValue()>Integer.MAX_VALUE)continue;
            candidates.add(new Candidate(projectId,version,((Number)attempt).intValue(),request));
        }
        int end=Math.addExact(root.getInteger("offset"),items.size());
        var next=end<root.getInteger("total")?new Cursor(id,end,false):new Cursor(id,0,true);
        return new Page(candidates,next,items.size());
    }
    private static Document eligible() {
        var base=List.of(new Document("$eq",List.of("$$v.reviewStatus","PENDING")),new Document("$eq",List.of("$$v.scanResult.status","SCANNING")),
                new Document("$in",List.of("$$v.scanResult.scanState",List.of("QUEUED","SCANNING","REMOTE_REVIEW"))));
        var poll="$$v.scanResult.remotePoll";
        var validPoll=new Document("$and",List.of(new Document("$eq",List.of(new Document("$size",new Document("$objectToArray",poll)),3)),
                new Document("$in",List.of(new Document("$type",poll+".token"),List.of("string","null"))),
                new Document("$eq",List.of(new Document("$type",poll+".leaseUntil"),"date")),
                new Document("$eq",List.of(new Document("$type",poll+".nextPollAt"),"date")),
                new Document("$lte",List.of(poll+".leaseUntil","$$NOW")),new Document("$lte",List.of(poll+".nextPollAt","$$NOW"))));
        var available=new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type",poll),"missing")),true,
                new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type",poll),"object")),
                        new Document("$and",List.of(new Document("$eq",List.of("$$v.scanResult.scanState","REMOTE_REVIEW")),validPoll)),false))));
        var conditions=new ArrayList<Object>(base);conditions.add(available);return new Document("$and",conditions);
    }
    private static Document safeString(String field,int max) {return new Document("$cond",Arrays.asList(boundedString(field,max,false),field,null));}
    private static Document boundedString(String field,int max,boolean objectIdAllowed) {
        return new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type",field),"string")),
                new Document("$and",List.of(new Document("$gt",List.of(new Document("$strLenCP",field),0)),new Document("$lte",List.of(new Document("$strLenCP",field),max)))),
                objectIdAllowed?new Document("$eq",List.of(new Document("$type",field),"objectId")):false));
    }
    private static Document literal(Object value){return new Document("$literal",value);}
}
