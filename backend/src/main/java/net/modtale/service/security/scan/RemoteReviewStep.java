package net.modtale.service.security.scan;

import net.modtale.model.project.RemoteReviewBinding;
import net.modtale.service.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.concurrent.Semaphore;

@Service
@ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
public final class RemoteReviewStep {
    public record Outcome(String state,String remoteState) {}
    private final RemoteReviewPollStore polls;
    private final RemoteReviewClient client;
    private final StorageService storage;
    private final ScanCompletionService completion;
    private final Semaphore slots=new Semaphore(2);
    public RemoteReviewStep(RemoteReviewPollStore polls,RemoteReviewClient client,StorageService storage,ScanCompletionService completion) {
        this.polls=polls;this.client=client;this.storage=storage;this.completion=completion;
    }
    public Outcome advance(RemoteReviewBinding binding) {
        if(!slots.tryAcquire())return new Outcome("BUSY",null);
        RemoteReviewPollStore.Claim claim=null;
        try {
            claim=polls.claim(binding,120000);if(claim==null)return new Outcome("NO_WORK",null);
            var acquired=claim;
            var status=client.submitOrFind(binding,()->storage.downloadBounded(binding.filePath(),100*1024*1024),()->polls.isCurrent(acquired));
            if(binding.jobId()==null) {
                claim=polls.attachJob(claim,status.jobId());if(claim==null)return new Outcome("SUPERSEDED",null);
            }
            if("COMPLETED".equals(status.state())) {
                if(!polls.isCurrent(claim))return new Outcome("SUPERSEDED",null);
                boolean applied=completion.handleRemoteCompletedScan(claim,client.result(claim.binding()));
                return new Outcome(applied?"APPLIED":"SUPERSEDED",applied?"COMPLETED":null);
            }
            boolean saved=polls.recordStatusAndRelease(claim,status,10000);
            return new Outcome(saved?"RECORDED":"SUPERSEDED",saved?status.state():null);
        } catch(RemoteReviewClient.Superseded stale) {return new Outcome("SUPERSEDED",null);}
        catch(RuntimeException unavailable) {
            if(claim!=null)try {if(polls.release(claim,30000))return new Outcome("RETRY",null);}catch(RuntimeException unknown) { /* Ownership expires in the database. */ }
            return new Outcome("UNKNOWN",null);
        } finally {slots.release();}
    }
}
