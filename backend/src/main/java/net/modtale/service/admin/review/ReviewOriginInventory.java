package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.Project;
import net.modtale.model.project.RemoteReviewOrigin;
import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only inventory of retained origin fields, not proof of a job's origin or permission to reconcile it. */
@org.springframework.stereotype.Service
public final class ReviewOriginInventory {
    public enum OriginState { MISSING, INVALID, RECORDED, UNREADABLE_BINDING }
    public record Item(Object projectId,int versionIndex,String versionId,boolean ambiguousVersion,
                       String requestId,String jobId,OriginState originState) {}
    public record Page(List<Item> items,RemoteReviewDiscovery.Cursor next,int examined) {
        public Page {items=List.copyOf(items);}
    }
    private final MongoCollection<Document> projects;
    public ReviewOriginInventory(MongoTemplate mongo) {
        projects=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Page page(RemoteReviewDiscovery.Cursor cursor,int limit) {
        if(limit<1 || limit>64)throw new IllegalArgumentException("Invalid origin inventory page size");
        var terms=new ArrayList<Object>();
        terms.add(new Document("$cond",List.of(eq(type("$_id"),"string"),new Document("$and",List.of(
                new Document("$gt",List.of(new Document("$strLenCP","$_id"),0)),new Document("$lte",List.of(new Document("$strLenCP","$_id"),128)))),true)));
        if(cursor!=null)terms.add(new Document(cursor.afterProject()?"$gt":"$gte",List.of("$_id",literal(cursor.projectId()))));
        var match=new Document("_id",new Document("$type",List.of("string","objectId")))
                .append("versions",new Document("$elemMatch",new Document("scanResult.remoteReview",new Document("$exists",true).append("$ne",null))))
                .append("$expr",new Document("$and",terms));
        Object offset=cursor==null?literal(0):new Document("$cond",List.of(eq("$_id",literal(cursor.projectId())),cursor.offset(),0));
        var versions=new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of()));
        String binding="$$v.scanResult.remoteReview",origin=binding+".origin";
        var view=new Document("bindingType",type(binding)).append("versionId",text("$$v._id",128))
                .append("idCount",new Document("$size",new Document("$filter",new Document("input",versions).append("as","sibling").append("cond",eq("$$sibling._id","$$v._id")))))
                .append("requestId",text(binding+".requestId",36)).append("jobId",text(binding+".jobId",36))
                .append("originType",type(origin)).append("deployment",text(origin+".deploymentId",36)).append("caller",text(origin+".callerScope",64));
        var projection=new Document("_id",1).append("offset",offset).append("total",new Document("$size",versions))
                .append("items",new Document("$map",new Document("input",new Document("$slice",List.of(versions,offset,limit))).append("as","v").append("in",view)));
        var pipeline=List.of(new Document("$match",match),new Document("$sort",new Document("_id",1)),new Document("$limit",1),new Document("$project",projection));
        Document root;
        try(var rows=projects.withTimeout(5000,TimeUnit.MILLISECONDS).aggregate(pipeline).collation(Collation.builder().locale("simple").build())
                .allowDiskUse(false).maxTime(5,TimeUnit.SECONDS).batchSize(1).iterator()) {root=rows.hasNext()?rows.next():null;}
        if(root==null)return new Page(List.of(),null,0);
        var items=new ArrayList<Item>();var rows=root.getList("items",Document.class);int index=root.getInteger("offset");
        for(var row:rows) {
            String bindingType=row.getString("bindingType");
            if(!Set.of("missing","null").contains(bindingType)) {
                String version=row.getString("versionId");boolean valid=version!=null && !version.isBlank() && version.length()<=128 && version.chars().noneMatch(Character::isISOControl);
                boolean object="object".equals(bindingType);
                items.add(new Item(root.get("_id"),index,valid?version:null,!valid || row.getInteger("idCount",0)!=1,
                        object?uuid(row.getString("requestId")):null,object?uuid(row.getString("jobId")):null,object?originState(row):OriginState.UNREADABLE_BINDING));
            }
            index++;
        }
        var next=index<root.getInteger("total")?new RemoteReviewDiscovery.Cursor(root.get("_id"),index,false):new RemoteReviewDiscovery.Cursor(root.get("_id"),0,true);
        return new Page(items,next,rows.size());
    }
    private static OriginState originState(Document row) {
        if(Set.of("missing","null").contains(row.getString("originType")))return OriginState.MISSING;
        if(!"object".equals(row.getString("originType")))return OriginState.INVALID;
        try {new RemoteReviewOrigin(row.getString("deployment"),row.getString("caller"));return OriginState.RECORDED;}
        catch(IllegalArgumentException invalid){return OriginState.INVALID;}
    }
    private static String uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")?value:null;}
    private static Document type(String field){return new Document("$type",field);}
    private static Document eq(Object a,Object b){return new Document("$eq",List.of(a,b));}
    private static Document literal(Object value){return new Document("$literal",value);}
    private static Document text(String field,int max){return new Document("$cond",Arrays.asList(eq(type(field),"string"),new Document("$substrCP",List.of(field,0,max+1)),null));}
}
