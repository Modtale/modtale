package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.Project;
import net.modtale.model.project.RemoteReviewBinding;
import net.modtale.service.security.scan.RemoteReviewDiscovery;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only structural diagnostics. Positions identify stored rows, never approval or retry authority. */
@Service
public final class ReviewStateDiagnosticReader {
    public enum Reason { INVALID_PROJECT_ID, INVALID_VERSION_ID, DUPLICATE_VERSION_ID, INVALID_REQUEST,
        INVALID_ARTIFACT, INVALID_MANUAL_MODE, INVALID_SCAN_STATE, MISSING_BINDING, INVALID_BINDING,
        BINDING_MISMATCH, INVALID_POLL, POLL_WITHOUT_REMOTE_STATE }
    public record Item(Object projectId,int versionIndex,String versionId,List<Reason> reasons) {
        public Item { reasons=List.copyOf(reasons); }
    }
    public record Page(List<Item> items,RemoteReviewDiscovery.Cursor next,int examined) {
        public Page { items=List.copyOf(items); }
    }
    private final MongoCollection<Document> projects;
    public ReviewStateDiagnosticReader(MongoTemplate mongo) {
        projects=mongo.getCollection(mongo.getCollectionName(Project.class)).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public Page page(RemoteReviewDiscovery.Cursor cursor,int limit) {
        if(limit<1 || limit>64)throw new IllegalArgumentException("Invalid diagnostic page size");
        var scanning=new Document("reviewStatus","PENDING").append("scanResult.status","SCANNING");
        var terms=new ArrayList<Object>();terms.add(new Document("$cond",List.of(eq(type("$_id"),"string"),
                new Document("$and",List.of(new Document("$gt",List.of(new Document("$strLenCP","$_id"),0)),new Document("$lte",List.of(new Document("$strLenCP","$_id"),128)))),true)));
        if(cursor!=null)terms.add(new Document(cursor.afterProject()?"$gt":"$gte",List.of("$_id",literal(cursor.projectId()))));
        var match=new Document("_id",new Document("$type",List.of("string","objectId"))).append("versions",new Document("$elemMatch",scanning))
                .append("$expr",new Document("$and",terms));
        Object offset=cursor==null?literal(0):new Document("$cond",List.of(eq("$_id",literal(cursor.projectId())),cursor.offset(),0));
        var versions=new Document("$cond",List.of(new Document("$isArray","$versions"),"$versions",List.of()));
        var view=new Document("scanning",new Document("$and",List.of(eq("$$v.reviewStatus","PENDING"),eq("$$v.scanResult.status","SCANNING"))))
                .append("versionId",text("$$v._id",128)).append("filePath",text("$$v.fileUrl",4096)).append("hash",text("$$v.hash",64))
                .append("idCount",new Document("$size",new Document("$filter",new Document("input",versions).append("as","sibling")
                        .append("cond",eq("$$sibling._id","$$v._id")))));
        String scan="$$v.scanResult";
        view.append("state",text(scan+".scanState",64)).append("request",text(scan+".scanRequestId",36)).append("attempt",integer(scan+".scanAttempt"))
                .append("manualType",type(scan+".manualRescan")).append("manual",bool(scan+".manualRescan"))
                .append("bindingType",type(scan+".remoteReview")).append("pollType",type(scan+".remotePoll"));
        String binding=scan+".remoteReview";
        for(String key:List.of("projectId","versionId","requestId","filePath","artifactSha256","contextSha256","policyVersion","reviewConfigSha256","jobId"))
            view.append("binding_"+key,text(binding+"."+key,key.equals("filePath")?4096:128));
        view.append("binding_attempt",integer(binding+".attempt")).append("binding_manualType",type(binding+".manualRescan"))
                .append("binding_manual",bool(binding+".manualRescan")).append("binding_jobType",type(binding+".jobId"));
        view.append("originType",type(binding+".origin")).append("originDeployment",text(binding+".origin.deploymentId",36)).append("originCaller",text(binding+".origin.callerScope",64));
        String poll=scan+".remotePoll";
        view.append("pollSize",new Document("$cond",List.of(eq(type(poll),"object"),new Document("$size",new Document("$objectToArray",poll)),0)))
                .append("pollToken",text(poll+".token",36)).append("pollTokenType",type(poll+".token"))
                .append("pollLeaseType",type(poll+".leaseUntil")).append("pollNextType",type(poll+".nextPollAt"));
        var projection=new Document("_id",1).append("offset",offset).append("total",new Document("$size",versions))
                .append("items",new Document("$map",new Document("input",new Document("$slice",List.of(versions,offset,limit))).append("as","v").append("in",view)));
        var pipeline=List.of(new Document("$match",match),new Document("$sort",new Document("_id",1)),new Document("$limit",1),new Document("$project",projection));
        Document root;
        try(var rows=projects.aggregate(pipeline).collation(Collation.builder().locale("simple").build()).allowDiskUse(false).maxTime(5,TimeUnit.SECONDS).batchSize(1).iterator()) {
            root=rows.hasNext()?rows.next():null;
        }
        if(root==null)return new Page(List.of(),null,0);
        Object id=root.get("_id");String project=id instanceof ObjectId oid?oid.toHexString():(String)id;
        var rows=root.getList("items",Document.class);var items=new ArrayList<Item>();int index=root.getInteger("offset");
        for(var row:rows) {
            if(Boolean.TRUE.equals(row.get("scanning"))) {
                var reasons=classify(project,row);
                if(!reasons.isEmpty())items.add(new Item(id,index,validText(row.getString("versionId"),128)?row.getString("versionId"):null,reasons));
            }
            index++;
        }
        var next=index<root.getInteger("total")?new RemoteReviewDiscovery.Cursor(id,index,false):new RemoteReviewDiscovery.Cursor(id,0,true);
        return new Page(items,next,rows.size());
    }
    private static List<Reason> classify(String project,Document row) {
        var reasons=new ArrayList<Reason>();
        if(!validText(project,128))reasons.add(Reason.INVALID_PROJECT_ID);
        if(!validText(row.getString("versionId"),128))reasons.add(Reason.INVALID_VERSION_ID);
        if(row.getInteger("idCount",0)>1)reasons.add(Reason.DUPLICATE_VERSION_ID);
        if(!uuid(row.getString("request")) || row.getInteger("attempt",0)<1)reasons.add(Reason.INVALID_REQUEST);
        if(!validText(row.getString("filePath"),4096) || !digest(row.getString("hash")))reasons.add(Reason.INVALID_ARTIFACT);
        if(!manualType(row.getString("manualType")))reasons.add(Reason.INVALID_MANUAL_MODE);
        String state=row.getString("state");boolean remote="REMOTE_REVIEW".equals(state);
        if(!Set.of("QUEUED","SCANNING","REMOTE_REVIEW").contains(Objects.toString(state,"")))reasons.add(Reason.INVALID_SCAN_STATE);
        String bindingType=row.getString("bindingType");
        if(Set.of("missing","null").contains(bindingType)) {if(remote)reasons.add(Reason.MISSING_BINDING);}
        else {
            RemoteReviewBinding binding=null;
            try {
                if(!"object".equals(bindingType) || !"bool".equals(row.getString("binding_manualType"))
                        || !Set.of("missing","null","string").contains(row.getString("binding_jobType"))
                        || "string".equals(row.getString("binding_jobType")) && !uuid(row.getString("binding_jobId")))throw new IllegalArgumentException();
                net.modtale.model.project.RemoteReviewOrigin origin=null;
                if(!Set.of("missing","null").contains(row.getString("originType"))) {
                    if(!"object".equals(row.getString("originType")))throw new IllegalArgumentException();
                    origin=new net.modtale.model.project.RemoteReviewOrigin(row.getString("originDeployment"),row.getString("originCaller"));
                }
                binding=new RemoteReviewBinding(row.getString("binding_projectId"),row.getString("binding_versionId"),row.getString("binding_requestId"),row.getInteger("binding_attempt",0),
                        row.getString("binding_filePath"),row.getString("binding_artifactSha256"),row.getString("binding_contextSha256"),row.getString("binding_policyVersion"),
                        row.getString("binding_reviewConfigSha256"),row.getString("binding_jobId"),Boolean.TRUE.equals(row.get("binding_manual")),origin);
            } catch(IllegalArgumentException invalid) {reasons.add(Reason.INVALID_BINDING);}
            if(binding!=null && (!remote || !project.equals(binding.projectId()) || !Objects.equals(row.getString("versionId"),binding.versionId())
                    || !Objects.equals(row.getString("request"),binding.requestId()) || row.getInteger("attempt",0)!=binding.attempt()
                    || !Objects.equals(row.getString("filePath"),binding.filePath()) || !Objects.equals(row.getString("hash"),binding.artifactSha256())
                    || Boolean.TRUE.equals(row.get("manual"))!=binding.manualRescan()))reasons.add(Reason.BINDING_MISMATCH);
        }
        String pollType=row.getString("pollType");
        if(!"missing".equals(pollType)) {
            if(!remote)reasons.add(Reason.POLL_WITHOUT_REMOTE_STATE);
            if(!"object".equals(pollType) || row.getInteger("pollSize",0)!=3 || !"date".equals(row.getString("pollLeaseType"))
                    || !"date".equals(row.getString("pollNextType")) || !("null".equals(row.getString("pollTokenType"))
                    || "string".equals(row.getString("pollTokenType")) && uuid(row.getString("pollToken"))))reasons.add(Reason.INVALID_POLL);
        }
        return reasons;
    }
    private static boolean manualType(String type){return "missing".equals(type) || "bool".equals(type);}
    private static boolean validText(String s,int max){return s!=null && !s.isBlank() && s.length()<=max && s.chars().noneMatch(Character::isISOControl);}
    private static boolean uuid(String s){return s!=null && s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static boolean digest(String s){return s!=null && s.matches("[0-9a-f]{64}");}
    private static Document type(String field){return new Document("$type",field);}
    private static Document eq(Object a,Object b){return new Document("$eq",List.of(a,b));}
    private static Document literal(Object v){return new Document("$literal",v);}
    private static Document text(String field,int max){return new Document("$cond",Arrays.asList(eq(type(field),"string"),new Document("$substrCP",List.of(field,0,max+1)),null));}
    private static Document bool(String field){return new Document("$cond",List.of(eq(type(field),"bool"),field,false));}
    private static Document integer(String field){return new Document("$cond",List.of(new Document("$in",List.of(type(field),List.of("int","long"))),
            new Document("$convert",new Document("input",field).append("to","int").append("onError",0).append("onNull",0)),0));}
}
