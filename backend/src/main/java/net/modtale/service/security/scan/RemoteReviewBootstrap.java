package net.modtale.service.security.scan;

import net.modtale.model.project.RemoteReviewBinding;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
public final class RemoteReviewBootstrap {
    public record Prepared(String state,RemoteReviewBinding binding) {}
    private final RemoteReviewPersistence persistence;
    private final RemoteReviewClient client;
    private final RemoteReviewStep step;
    public RemoteReviewBootstrap(RemoteReviewPersistence persistence,RemoteReviewClient client,RemoteReviewStep step) {
        this.persistence=persistence;this.client=client;this.step=step;
    }
    public Prepared prepare(String projectId,String versionId,int attempt,String requestId) {return prepare(projectId,versionId,attempt,requestId,()->true);}
    private Prepared prepare(String projectId,String versionId,int attempt,String requestId,java.util.function.BooleanSupplier running) {
        if(!running.getAsBoolean())throw new RemoteReviewClient.Superseded();
        var current=persistence.current(projectId,versionId,attempt,requestId);
        if(current==null)return new Prepared("NO_WORK",null);
        if(current.getScanResult().getRemoteReview()!=null || "REMOTE_REVIEW".equals(current.getScanResult().getScanState()))
            return retainedOrBroken(projectId,versionId,attempt,requestId,running);
        String context=ArtifactReviewContext.automaticallyReviewableFingerprint(current);
        if(context==null) {
            if(!running.getAsBoolean())throw new RemoteReviewClient.Superseded();
            return new Prepared(persistence.finishUnsupportedContext(projectId,current)?"UNAVAILABLE":"NO_WORK",null);
        }
        if(!running.getAsBoolean())throw new RemoteReviewClient.Superseded();
        var configuration=client.configuration();
        if(!running.getAsBoolean())throw new RemoteReviewClient.Superseded();
        var binding=new RemoteReviewBinding(projectId,versionId,requestId,attempt,current.getFileUrl(),current.getHash(),context,
                configuration.policyVersion(),configuration.reviewConfigSha256(),null,current.getScanResult().isManualRescan(),configuration.origin());
        try { persistence.bind(current,binding); }
        catch(RuntimeException unknown) {
            var recovered=retainedOrBroken(projectId,versionId,attempt,requestId,running);
            if(!"NO_WORK".equals(recovered.state()))return recovered;
            throw unknown;
        }
        return retainedOrBroken(projectId,versionId,attempt,requestId,running);
    }
    private Prepared retainedOrBroken(String projectId,String versionId,int attempt,String requestId,java.util.function.BooleanSupplier running) {
        var retained=persistence.retained(projectId,versionId,attempt,requestId);
        if(retained!=null && retained.origin()!=null)return new Prepared("READY",retained);
        if(!running.getAsBoolean())throw new RemoteReviewClient.Superseded();
        return new Prepared(persistence.finishBrokenBinding(projectId,versionId,attempt,requestId)==null?"NO_WORK":"UNAVAILABLE",null);
    }
    public RemoteReviewStep.Outcome advance(String projectId,String versionId,int attempt,String requestId) {return advance(projectId,versionId,attempt,requestId,()->true);}
    public RemoteReviewStep.Outcome advance(String projectId,String versionId,int attempt,String requestId,java.util.function.BooleanSupplier running) {
        try {
            var prepared=prepare(projectId,versionId,attempt,requestId,running);
            return prepared.binding()==null?new RemoteReviewStep.Outcome(prepared.state(),null):step.advance(prepared.binding(),running);
        } catch(RemoteReviewClient.Superseded stopped) {return new RemoteReviewStep.Outcome("SHUTDOWN",null);
        } catch(RuntimeException unavailable) {return new RemoteReviewStep.Outcome("RETRY",null);}
    }
}
