package net.modtale.service.security.scan;

import java.util.UUID;
import net.modtale.model.project.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteReviewBootstrapPreflightTest {
    @Test void currentExactReuseCompletesWithoutSubmittingReviewJob() {
        var persistence=mock(RemoteReviewPersistence.class);
        var client=mock(RemoteReviewClient.class);
        var step=mock(RemoteReviewStep.class);
        var reuse=mock(ExactArtifactReusePreflight.class);
        String project="p",version="v",request=UUID.randomUUID().toString(),policy="warden-3.0.0:"+"a".repeat(64);
        var queued=new ScanResult();queued.setStatus(ScanStatus.SCANNING);queued.setScanState("QUEUED");
        queued.setScanAttempt(1);queued.setScanRequestId(request);
        var target=new ProjectVersion();target.setId(version);target.setHash("b".repeat(64));target.setFileUrl("original.zip");target.setScanResult(queued);
        when(persistence.current(project,version,1,request)).thenReturn(target);
        when(client.configuration()).thenReturn(new RemoteReviewClient.Configuration(policy,"c".repeat(64),
                new RemoteReviewOrigin(UUID.randomUUID().toString(),"d".repeat(64))));
        when(reuse.tryComplete(project,target,policy)).thenReturn(true);

        var prepared=new RemoteReviewBootstrap(persistence,client,step,reuse).prepare(project,version,1,request);
        assertEquals("SCANNER_COMPLETED",prepared.state());
        assertNull(prepared.binding());
        verify(persistence,never()).bind(any(),any());
        verifyNoInteractions(step);
    }
}
