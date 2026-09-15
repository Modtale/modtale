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
        verifyNoInteractions(persistence,storage,completion);
        verify(warden,never()).scanFile(any(),any());
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
        verifyNoInteractions(storage,completion);
        verify(warden,never()).scanFile(any(),any());
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
    @Test void remoteModeLeavesQueuedWorkForSchedulerAndDisablesLegacyRecovery() {
        var warden=mock(WardenClientService.class);when(warden.remoteJobsEnabled()).thenReturn(true);
        var storage=mock(StorageService.class);var persistence=mock(ScanPersistenceService.class);
        var completion=mock(ScanCompletionService.class);var recovery=mock(ScanRecoveryService.class);
        var service=new ScanExecutionService(warden,storage,r->{fail("Legacy executor used");},persistence,completion,recovery);
        service.enqueueBackgroundScan("p","v","file","mod.zip",false,1,"request");
        service.enqueueBackgroundScan("p","v","file","mod.zip",true,2,"manual-request");
        service.enqueueBackgroundScan("p","v","file","mod.zip",false,1);
        service.recoverStaleScanningVersions();
        verifyNoInteractions(storage,persistence,completion,recovery);verify(warden,never()).scanFile(any(),any());
    }
    @Test void delayedLegacyTaskRechecksModeBeforeClaiming() {
        var warden=mock(WardenClientService.class);var work=new ArrayList<Runnable>();
        var storage=mock(StorageService.class);var persistence=mock(ScanPersistenceService.class);
        var completion=mock(ScanCompletionService.class);var recovery=mock(ScanRecoveryService.class);
        var service=new ScanExecutionService(warden,storage,work::add,persistence,completion,recovery);
        service.enqueueBackgroundScan("p","v","file","mod.zip",false,1,"request");
        when(warden.remoteJobsEnabled()).thenReturn(true);work.getFirst().run();
        verifyNoInteractions(storage,persistence,completion,recovery);verify(warden,never()).scanFile(any(),any());
    }
    @Test void legacyModeRetainsRecovery() {
        var recovery=mock(ScanRecoveryService.class);
        new ScanExecutionService(mock(WardenClientService.class),mock(StorageService.class),Runnable::run,
                mock(ScanPersistenceService.class),mock(ScanCompletionService.class),recovery).recoverStaleScanningVersions();
        verify(recovery).recoverStaleScanningVersions(any());
    }
    @Test void springBindsModeAndDirectLegacyCallsCannotBypassIt() {
        var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(net.modtale.config.properties.AppWardenProperties.class,()->new net.modtale.config.properties.AppWardenProperties("http://127.0.0.1:1","fixture",true,3,15))
                .withUserConfiguration(WardenClientService.class);
        runner.withPropertyValues("app.warden.jobs.enabled=true").run(context->{
            assertNull(context.getStartupFailure());var client=context.getBean(WardenClientService.class);
            assertTrue(client.remoteJobsEnabled());
            assertEquals("Synchronous scans are disabled in remote jobs mode",assertThrows(IllegalStateException.class,()->client.scanFile(null,null)).getMessage());
        });
        runner.run(context->{assertNull(context.getStartupFailure());assertFalse(context.getBean(WardenClientService.class).remoteJobsEnabled());});
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
