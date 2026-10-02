package net.modtale.service.admin.review;

import org.springframework.data.mongodb.core.MongoTemplate;
import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Seeds prior successful observation checkpoints through their real validation/write path. */
public final class ProjectMutationProgressTestSupport {
    private ProjectMutationProgressTestSupport() {}
    public static void retain(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationJobAccounting accounting,
            ProjectMutationAdmissionAttempts.Scope scope,ProjectMutationPriorWorkReader.Work work,ProjectMutationJobAccounting.Receipt receipt) {
        budget.call(allowed->{new ProjectMutationObservationProgress(mongo,accounting).retain(scope,work,receipt,allowed);return null;},()->true);
    }
    public static void assertReadBatchBoundaries(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history,
            RemoteReviewClient client,ProjectMutationAdmissionAttempts.Scope scope,Map<String,String> ids)throws Exception {
        var verified=spy(history);var permission=new AtomicBoolean(true);
        try(var accounting=new ProjectMutationJobAccounting(mongo,budget,verified,client,1)){
            budget.call(allowed->{
                var batch=accounting.readBatch(scope.projectId(),()->allowed.getAsBoolean()&&permission.get());
                try(batch){
                    for(var entry:ids.entrySet())assertEquals("OBSERVED",batch.receipt(entry.getValue(),scope.projectId(),scope.mutationId(),entry.getKey()).state());
                    verify(verified,times(1)).read(eq(scope.projectId()),eq(scope.mutationId()),any());
                    var first=ids.entrySet().iterator().next();
                    assertThrows(IllegalStateException.class,()->batch.receipt(first.getValue(),scope.projectId().toString(),scope.mutationId(),first.getKey()));
                    permission.set(false);assertThrows(SecurityException.class,()->batch.receipt(first.getValue(),scope.projectId(),scope.mutationId(),first.getKey()));permission.set(true);
                    try(var worker=Executors.newVirtualThreadPerTaskExecutor()){
                        var task=worker.submit(()->batch.receipt(first.getValue(),scope.projectId(),scope.mutationId(),first.getKey()));
                        var failure=assertThrows(ExecutionException.class,()->task.get(3,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,failure.getCause());
                    }
                    var row=mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).find(new Document("_id",first.getValue())).first();
                    mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).updateOne(new Document("_id",first.getValue()),new Document("$set",new Document("intent.beforeSha256","0".repeat(64))));
                    assertThrows(IllegalStateException.class,()->batch.receipt(first.getValue(),scope.projectId(),scope.mutationId(),first.getKey()));
                    mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).replaceOne(new Document("_id",first.getValue()),row);
                }
                var first=ids.entrySet().iterator().next();assertThrows(IllegalStateException.class,()->batch.receipt(first.getValue(),scope.projectId(),scope.mutationId(),first.getKey()));return null;
            },()->true);
            mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",scope.mutationId()),new Document("$set",new Document("state","UNKNOWN")));
            assertThrows(IllegalStateException.class,()->budget.call(allowed->{try(var batch=accounting.readBatch(scope.projectId(),allowed)){var first=ids.entrySet().iterator().next();return batch.receipt(first.getValue(),scope.projectId(),scope.mutationId(),first.getKey());}},()->true));
        }
    }
}
