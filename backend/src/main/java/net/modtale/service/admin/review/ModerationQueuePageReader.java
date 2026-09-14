package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.dto.admin.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public final class ModerationQueuePageReader {
    public record Cursor(Object projectId,long versionIndex) {
        public Cursor {
            if (!(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.codePointCount(0,s.length())<=128)
                    || versionIndex<0 || versionIndex>16*1024*1024) throw new IllegalArgumentException("Invalid queue cursor");
        }
    }
    public record Page(List<AdminVerificationQueueItemDTO> items,Cursor next,int unavailableItems) {
        public Page { items=List.copyOf(items); }
    }
    private final MongoCollection<Document> projects;
    public ModerationQueuePageReader(MongoTemplate mongo) {
        projects=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Page page(Cursor cursor,int limit) {
        if(limit<1 || limit>50)throw new IllegalArgumentException("Invalid queue page size");
        var stages=new ArrayList<Document>();
        var match=new Document("status",new Document("$in",List.of("PENDING","PUBLISHED")))
                .append("_id",new Document("$type",List.of("string","objectId")));
        var idBound=new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type","$_id"),"string")),
                new Document("$and",List.of(new Document("$gt",List.of(new Document("$strLenCP","$_id"),0)),new Document("$lte",List.of(new Document("$strLenCP","$_id"),128)))),true));
        match.append("$expr",cursor==null?idBound:new Document("$and",List.of(idBound,new Document("$gte",List.of("$_id",literal(cursor.projectId()))))));
        stages.add(new Document("$match",match));stages.add(new Document("$sort",new Document("_id",1)));
        var versions=new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of()));
        var pendingOrScanning=new Document("$filter",new Document("input",versions).append("as","v").append("cond",new Document("$or",List.of(
                new Document("$eq",List.of("$$v.reviewStatus","PENDING")),new Document("$eq",List.of("$$v.scanResult.status","SCANNING"))))));
        var projectOnly=new Document("$and",List.of(new Document("$eq",List.of("$status","PENDING")),new Document("$eq",List.of(new Document("$size",pendingOrScanning),0))));
        stages.add(new Document("$set",new Document("queueProjectOnly",projectOnly)
                .append("queueVersion",new Document("$cond",List.of(projectOnly,Collections.singletonList(null),versions)))));
        stages.add(new Document("$unwind",new Document("path","$queueVersion").append("includeArrayIndex","queueIndex").append("preserveNullAndEmptyArrays",true)));
        stages.add(new Document("$match",new Document("$or",List.of(new Document("queueProjectOnly",true),
                new Document("queueVersion.reviewStatus","PENDING").append("queueVersion.scanResult.status",new Document("$ne","SCANNING"))))));
        if(cursor!=null)stages.add(new Document("$match",new Document("$expr",new Document("$or",List.of(
                new Document("$gt",List.of("$_id",literal(cursor.projectId()))),new Document("$gt",List.of("$queueIndex",cursor.versionIndex())))))));
        stages.add(new Document("$limit",limit+1));
        var projection=new Document("_id",1).append("queueIndex",1).append("queueProjectOnly",1)
                .append("title",text("$title",256)).append("description",text("$description",1024)).append("author",text("$author",128))
                .append("imageUrl",text("$imageUrl",2048)).append("classification",text("$classification",32)).append("status",1).append("updatedAt",text("$updatedAt",64))
                .append("versionId",text("$queueVersion._id",129)).append("versionNumber",text("$queueVersion.versionNumber",128))
                .append("changelog",text("$queueVersion.changelog",1024)).append("scanStatus",text("$queueVersion.scanResult.status",32))
                .append("verdict",text("$queueVersion.scanResult.verdict",32)).append("scanState",text("$queueVersion.scanResult.scanState",64));
        for(String field:List.of("riskScore","knownIssueCount","newIssueCount","escalatedIssueCount"))projection.append(field,new Document("$convert",new Document("input","$queueVersion.scanResult."+field).append("to","int").append("onError",0).append("onNull",0)));
        projection.append("idCount",new Document("$size",new Document("$filter",new Document("input",versions).append("as","v")
                .append("cond",new Document("$eq",List.of("$$v._id","$queueVersion._id"))))));
        stages.add(new Document("$project",projection));
        var rows=new ArrayList<Document>();
        try(var iterator=projects.aggregate(stages).collation(Collation.builder().locale("simple").build()).allowDiskUse(false)
                .maxTime(5,TimeUnit.SECONDS).batchSize(limit+1).iterator()) {while(iterator.hasNext())rows.add(iterator.next());}
        boolean more=rows.size()>limit;if(more)rows.removeLast();
        var items=new ArrayList<AdminVerificationQueueItemDTO>();int unavailable=0;
        for(var row:rows) {
            Object id=row.get("_id");String projectId=id instanceof ObjectId oid?oid.toHexString():(String)id;
            boolean only=Boolean.TRUE.equals(row.get("queueProjectOnly"));String versionId=row.getString("versionId");
            if(!validId(projectId) || !only && (!validId(versionId) || row.getInteger("idCount",0)!=1)) {unavailable++;continue;}
            var status=enumValue(ScanStatus.class,row.getString("scanStatus"));
            if(row.getString("scanStatus")!=null && status==null) {unavailable++;continue;}
            var scan=row.getString("scanStatus")==null?null:new AdminVerificationQueueScanDTO(status,row.getString("verdict"),row.getString("scanState"),
                    row.getInteger("riskScore",0),row.getInteger("knownIssueCount",0),row.getInteger("newIssueCount",0),row.getInteger("escalatedIssueCount",0));
            items.add(new AdminVerificationQueueItemDTO(projectId,row.getString("title"),row.getString("description"),row.getString("author"),row.getString("imageUrl"),
                    enumValue(ProjectClassification.class,row.getString("classification")),enumValue(ProjectStatus.class,row.getString("status")),row.getString("updatedAt"),
                    only?null:new AdminVerificationQueueVersionDTO(versionId,row.getString("versionNumber"),row.getString("changelog"),ProjectVersion.ReviewStatus.PENDING,scan)));
        }
        Cursor next=null;
        if(more) {var last=rows.getLast();next=new Cursor(last.get("_id"),((Number)last.get("queueIndex")).longValue());}
        return new Page(items,next,unavailable);
    }
    private static boolean validId(String s) {return s!=null && !s.isBlank() && s.codePointCount(0,s.length())<=128 && s.chars().noneMatch(Character::isISOControl);}
    private static <T extends Enum<T>> T enumValue(Class<T> type,String value) {try{return value==null?null:Enum.valueOf(type,value);}catch(IllegalArgumentException invalid){return null;}}
    private static Document text(String field,int limit) {return new Document("$cond",Arrays.asList(new Document("$eq",List.of(new Document("$type",field),"string")),new Document("$substrCP",List.of(field,0,limit)),null));}
    private static Document literal(Object value) {return new Document("$literal",value);}
}
