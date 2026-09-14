package net.modtale.service.security.scan;

import java.util.*;
import net.modtale.model.project.ScanResult;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanRequestIdentityExecutionTest {
    @Test void requestIdentitySurvivesQueueDelayAndReachesCompletion() {
        var work=new ArrayList<Runnable>(); var storage=mock(StorageService.class); var warden=mock(WardenClientService.class);
        var persistence=mock(ScanPersistenceService.class); var completion=mock(ScanCompletionService.class);
        var service=new ScanExecutionService(warden,storage,work::add,persistence,completion,mock(ScanRecoveryService.class));
        when(persistence.markAttemptRunning("p","v",1,"request-a")).thenReturn(true);
        byte[] bytes={1}; var result=new ScanResult(); when(storage.download("artifact")).thenReturn(bytes);
        when(warden.scanFile(bytes,"mod.jar")).thenReturn(result);
        service.enqueueBackgroundScan("p","v","artifact","mod.jar",false,1,"request-a");
        verifyNoInteractions(persistence,storage,warden,completion);
        work.getFirst().run();
        verify(completion).handleCompletedScan("p","v",1,false,result,"request-a");
        verify(persistence,never()).markAttemptRunning("p","v",1);
    }
    @Test void obsoleteRequestCannotStartStorageOrProviderWork() {
        var storage=mock(StorageService.class); var warden=mock(WardenClientService.class);
        var persistence=mock(ScanPersistenceService.class); var completion=mock(ScanCompletionService.class);
        var service=new ScanExecutionService(warden,storage,Runnable::run,persistence,completion,mock(ScanRecoveryService.class));
        service.enqueueBackgroundScan("p","v","artifact","mod.jar",false,1,"obsolete");
        verify(persistence).markAttemptRunning("p","v",1,"obsolete");
        verifyNoInteractions(storage,warden,completion);
    }
    @Test void workerFailureRetainsTheClaimedRequestIdentity() {
        var storage=mock(StorageService.class); var persistence=mock(ScanPersistenceService.class);
        var completion=mock(ScanCompletionService.class); var failure=new IllegalStateException("test failure");
        when(persistence.markAttemptRunning("p","v",1,"request-a")).thenReturn(true);
        when(storage.download("artifact")).thenThrow(failure);
        var service=new ScanExecutionService(mock(WardenClientService.class),storage,Runnable::run,persistence,completion,mock(ScanRecoveryService.class));
        service.enqueueBackgroundScan("p","v","artifact","mod.jar",false,1,"request-a");
        verify(completion).handleScanFailure("p","v","mod.jar",1,failure,"request-a");
    }
    @Test void requestIdsAreInternalAndIndependentOfAttemptCounters() throws Exception {
        var routing=new ScanRoutingService(null);
        var first=routing.createQueuedScanResult(1,"test");var replacement=routing.createQueuedScanResult(1,"test");
        assertNotEquals(first.getScanRequestId(),replacement.getScanRequestId());
        assertEquals(first.getScanRequestId(),UUID.fromString(first.getScanRequestId()).toString());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        assertFalse(mapper.writeValueAsString(first).contains("scanRequestId"));
        assertNull(mapper.readValue("{\"scanRequestId\":\"forged\"}",ScanResult.class).getScanRequestId());
    }
}
