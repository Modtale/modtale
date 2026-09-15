package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Durable scheduling bookkeeping only. Callers must authenticate candidates and outcome evidence. */
public final class ProjectMutationAdmissionAttempts {
    public static final String COLLECTION="project_mutation_admission_attempts";
    public enum Outcome { WAITING, ATTENTION, ADMITTED }
    public record Scope(Object projectId,String versionId,String mutationId,String requestId,int scanAttempt) {
        public Scope {
            if(!(projectId instanceof ObjectId || projectId instanceof String s && !s.isEmpty() && s.length()<=128)
                    || versionId==null || versionId.isBlank() || versionId.length()>128 || versionId.chars().anyMatch(Character::isISOControl)
                    || !uuid(mutationId) || !uuid(requestId) || scanAttempt<1)throw invalid();
        }
    }
    public record Claim(Scope scope,int sequence,String token,String decisionId,String heldSha256) {}
    public record Status(String state,int attempts,Claim current,Outcome outcome,Long nextAttemptAt) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> records;private final ReviewRepairWorkflow budget;private final int maxAttempts;private final long cooldownMillis;
    public ProjectMutationAdmissionAttempts(MongoTemplate mongo,ReviewRepairWorkflow budget){this(mongo,budget,256,60000);}
    public ProjectMutationAdmissionAttempts(MongoTemplate mongo,ReviewRepairWorkflow budget,int maxAttempts,long cooldownMillis) {
        if(maxAttempts<1 || maxAttempts>256 || cooldownMillis<100 || cooldownMillis>3600000)throw invalid();this.maxAttempts=maxAttempts;this.cooldownMillis=cooldownMillis;this.budget=Objects.requireNonNull(budget);
        records=mongo.getCollection(COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(5,TimeUnit.SECONDS)).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public Claim begin(Scope scope,String heldSha256,BooleanSupplier permitted){return budget.call(allowed->beginWithinBudget(scope,heldSha256,allowed),permitted);}
    Claim beginWithinBudget(Scope scope,String heldSha256,BooleanSupplier allowed) {
        permission(allowed);if(scope==null || !sha(heldSha256))throw invalid();var stored=read(scope.requestId());
        if(stored==null) {
            var initial=new Document("_id",scope.requestId()).append("scope",scope(scope)).append("state","WAITING").append("nextAttemptAt",new Date(0)).append("attempts",List.of());
            permission(allowed);try{ReviewRepairIo.collection(records).insertOne(initial);}catch(MongoException uncertain){/* Exact read-back below; creation itself never grants a claim. */}
            stored=read(scope.requestId());
        }
        var status=decode(scope,stored);if(!"WAITING".equals(status.state()) || status.attempts()>=maxAttempts)return null;
        int sequence=status.attempts()+1;String token=UUID.randomUUID().toString(),decision=decision(scope,sequence);
        var attempt=new Document("sequence",sequence).append("token",token).append("decisionId",decision).append("heldSha256",heldSha256);
        var nextAttempt=new Document("$mergeObjects",List.of(literal(attempt),new Document("startedAt","$$NOW")));
        var query=exact(stored);query.append("$expr",new Document("$and",List.of(new Document("$eq",List.of("$$ROOT",literal(stored))),new Document("$lte",List.of("$nextAttemptAt","$$NOW")))));
        permission(allowed);
        try {
            var result=ReviewRepairIo.collection(records).findOneAndUpdate(query,List.of(new Document("$set",new Document("state","RUNNING")
                    .append("attempts",new Document("$concatArrays",List.of("$attempts",List.of(nextAttempt)))))),new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
            if(result==null)return null;var found=decode(scope,result);permission(allowed);return token.equals(found.current().token())?found.current():null;
        }catch(MongoException uncertain){var found=decode(scope,read(scope.requestId()));permission(allowed);return found.current()!=null && token.equals(found.current().token())?found.current():null;}
    }
    public Status status(Scope scope,BooleanSupplier permitted){return budget.call(allowed->{permission(allowed);var value=read(scope.requestId());var result=value==null?new Status("ABSENT",0,null,null,null):decode(scope,value);permission(allowed);return result;},permitted);}
    public Status finish(Claim claim,Outcome outcome,BooleanSupplier permitted){return budget.call(allowed->finishWithinBudget(claim,outcome,allowed),permitted);}
    Status finishWithinBudget(Claim claim,Outcome outcome,BooleanSupplier allowed) {
        permission(allowed);Objects.requireNonNull(outcome);var stored=read(claim.scope().requestId());var current=decode(claim.scope(),stored);
        if(!claim.equals(current.current()))throw invalid();if(!"RUNNING".equals(current.state())){if(outcome!=current.outcome())throw invalid();return current;}
        String state=outcome==Outcome.WAITING && current.attempts()>=maxAttempts?"ATTENTION":outcome.name();
        var history=new ArrayList<>(stored.getList("attempts",Document.class));var last=history.removeLast();
        var completed=new Document("$mergeObjects",List.of(literal(last),new Document("outcome",outcome.name()).append("finishedAt","$$NOW")));
        var fields=new Document("state",state).append("nextAttemptAt",new Document("$add",List.of("$$NOW",cooldownMillis)))
                .append("attempts",new Document("$concatArrays",List.of(literal(history),List.of(completed))));
        permission(allowed);try{ReviewRepairIo.collection(records).updateOne(exact(stored),List.of(new Document("$set",fields)),new UpdateOptions().collation(BINARY));}
        catch(MongoException uncertain){/* A lost finish reply never starts another attempt. */}
        var found=decode(claim.scope(),read(claim.scope().requestId()));permission(allowed);
        if(!claim.equals(found.current()) || found.outcome()!=outcome)throw invalid();return found;
    }
    private Status decode(Scope scope,Document stored) {
        if(stored==null || stored.size()!=5 || !scope.requestId().equals(stored.get("_id")) || !(stored.get("scope") instanceof Document raw)
                || !Arrays.equals(bytes(raw),bytes(scope(scope))) || !(stored.get("nextAttemptAt") instanceof Date next))throw invalid();
        var history=stored.getList("attempts",Document.class);if(history==null || history.size()>256)throw invalid();String state=stored.getString("state");
        if(history.isEmpty()){if(!"WAITING".equals(state) || next.getTime()!=0)throw invalid();return new Status(state,0,null,null,0L);}
        Claim claim=null;Outcome outcome=null;Date previous=null;
        for(int i=0;i<history.size();i++) {
            var item=history.get(i);boolean last=i==history.size()-1;
            if(!Integer.valueOf(i+1).equals(item.get("sequence")) || !uuid(item.getString("token")) || !decision(scope,i+1).equals(item.get("decisionId"))
                    || !sha(item.getString("heldSha256")) || !(item.get("startedAt") instanceof Date start) || previous!=null && start.before(previous))throw invalid();
            claim=new Claim(scope,i+1,item.getString("token"),item.getString("decisionId"),item.getString("heldSha256"));outcome=null;
            if(item.size()==5){if(!last || !"RUNNING".equals(state))throw invalid();}
            else {
                if(item.size()!=7 || !(item.get("finishedAt") instanceof Date end) || end.before(start))throw invalid();outcome=Outcome.valueOf(item.getString("outcome"));previous=end;
                if(!last && outcome!=Outcome.WAITING || last && (!state.equals(outcome.name()) && !(outcome==Outcome.WAITING && "ATTENTION".equals(state))))throw invalid();
                if(last && next.getTime()<=end.getTime())throw invalid();
            }
        }
        return new Status(state,history.size(),claim,outcome,next.getTime());
    }
    private Document read(String id){return ReviewRepairIo.collection(records).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static Document exact(Document stored){return new Document("_id",stored.get("_id")).append("$expr",new Document("$eq",List.of("$$ROOT",literal(stored))));}
    private static Document scope(Scope s){return new Document("projectId",s.projectId()).append("versionId",s.versionId()).append("mutationId",s.mutationId()).append("requestId",s.requestId()).append("scanAttempt",s.scanAttempt());}
    private static String decision(Scope scope,int sequence){return UUID.nameUUIDFromBytes(("mutation-auto-admission-1:"+scope.requestId()+":"+sequence).getBytes(StandardCharsets.UTF_8)).toString();}
    private static Document literal(Object value){return new Document("$literal",value);}
    private static byte[] bytes(Document doc){var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();var bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static boolean uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static boolean sha(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private static void permission(BooleanSupplier permitted){if(!permitted.getAsBoolean())throw new SecurityException("Automatic admission attempt is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Automatic admission attempt changed or is unavailable");}
}
