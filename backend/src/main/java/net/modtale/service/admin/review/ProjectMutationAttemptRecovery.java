package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Finds interrupted bookkeeping and reconciles only an already committed, authenticated admission. */
public final class ProjectMutationAttemptRecovery {
    public static final String INDEX="mutation_attempt_state_id";
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    public record Page(List<ProjectMutationAdmissionAttempts.Scope> candidates,String next,int examined,int unavailable) {public Page{candidates=List.copyOf(candidates);}}
    private final MongoCollection<Document> records;private final ReviewRepairWorkflow budget;private final ProjectMutationAdmissionAttempts attempts;private final ProjectMutationAdmissionReader admissions;
    public ProjectMutationAttemptRecovery(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationAdmissionAttempts attempts,ReviewSnapshotArchive archive) {
        this.budget=Objects.requireNonNull(budget);this.attempts=Objects.requireNonNull(attempts);admissions=new ProjectMutationAdmissionReader(mongo,archive);
        records=mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public void initialize(){records.withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).createIndex(new Document("state",1).append("_id",1),new IndexOptions().name(INDEX).collation(BINARY));}
    public Page page(String cursor,int limit) {
        if(limit<1 || limit>64 || cursor!=null && !cursor.matches(UUID))throw invalid();
        var ids=new Document("$type","string").append("$regex","^"+UUID+"$");if(cursor!=null)ids.append("$gt",cursor);
        var match=new Document("state","RUNNING").append("_id",ids).append("$expr",new Document("$eq",List.of(new Document("$type","$state"),"string")));
        var projection=new Document("_id",1).append("projectId",new Document("$cond",Arrays.asList(new Document("$eq",List.of(new Document("$type","$scope.projectId"),"objectId")),"$scope.projectId",safeString("$scope.projectId",128))))
                .append("versionId",safeString("$scope.versionId",128)).append("mutationId",safeString("$scope.mutationId",36)).append("requestId",safeString("$scope.requestId",36))
                .append("scopeSize",new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type","$scope"),"object")),new Document("$size",new Document("$objectToArray","$scope")),-1)))
                .append("attempt",new Document("$cond",Arrays.asList(new Document("$in",List.of(new Document("$type","$scope.scanAttempt"),List.of("int","long"))),"$scope.scanAttempt",null)));
        var rows=ReviewRepairIo.collection(records).aggregate(List.of(new Document("$match",match),new Document("$sort",new Document("_id",1)),new Document("$limit",limit+1),new Document("$project",projection)))
                .hintString(INDEX).collation(BINARY).allowDiskUse(false).maxTime(5,TimeUnit.SECONDS).batchSize(limit+1).into(new ArrayList<>());
        var candidates=new ArrayList<ProjectMutationAdmissionAttempts.Scope>();int unavailable=0,count=Math.min(limit,rows.size());
        for(var row:rows.subList(0,count)) {
            try {
                if(!Integer.valueOf(5).equals(row.get("scopeSize")) || !row.get("_id").equals(row.get("requestId")) || !(row.get("attempt") instanceof Number number) || number.longValue()<1 || number.longValue()>Integer.MAX_VALUE)throw invalid();
                candidates.add(new ProjectMutationAdmissionAttempts.Scope(row.get("projectId"),row.getString("versionId"),row.getString("mutationId"),row.getString("requestId"),((Number)row.get("attempt")).intValue()));
            }catch(IllegalStateException | ClassCastException malformed){unavailable++;}
        }
        return new Page(candidates,rows.size()>limit?rows.get(count-1).getString("_id"):null,count,unavailable);
    }
    public String recover(ProjectMutationAdmissionAttempts.Scope scope,BooleanSupplier running) {
        return budget.call(allowed->{
            var status=attempts.statusWithinBudget(scope,allowed);if(!"RUNNING".equals(status.state()))return status.state();
            var claim=status.current();ProjectMutationAdmissionReader.History proof;
            try{proof=admissions.read(scope.projectId(),scope.requestId(),allowed);}catch(IllegalStateException unavailable){return "UNRESOLVED";}
            var decision=proof.decision();
            if(!ProjectMutationAutomaticAdmission.ACTOR.equals(proof.source().actorId()) || !claim.decisionId().equals(decision.id()) || !claim.heldSha256().equals(decision.heldSha256())
                    || !scope.mutationId().equals(decision.mutationId()) || !scope.versionId().equals(decision.versionId()) || scope.scanAttempt()!=decision.binding().attempt())return "UNRESOLVED";
            return attempts.finishWithinBudget(claim,ProjectMutationAdmissionAttempts.Outcome.ADMITTED,allowed).state();
        },running);
    }
    private static Document safeString(String field,int max) {return new Document("$cond",Arrays.asList(new Document("$cond",List.of(new Document("$eq",List.of(new Document("$type",field),"string")),new Document("$and",List.of(new Document("$gt",List.of(new Document("$strLenCP",field),0)),new Document("$lte",List.of(new Document("$strLenCP",field),max)))),false)),field,null));}
    private static IllegalStateException invalid(){return new IllegalStateException("Invalid automatic admission recovery scope");}
}
