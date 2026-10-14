package net.modtale.service.admin.review;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMutationWorkflowTest {
    final ProjectMutationPreparation preparation=mock(ProjectMutationPreparation.class);
    final ProjectMutationExecutor executor=mock(ProjectMutationExecutor.class);
    final ProjectMutationReferenceReader history=mock(ProjectMutationReferenceReader.class);
    final AtomicLong time=new AtomicLong();
    final ReviewRepairWorkflow budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1,1000,time::get);
    final ProjectMutationWorkflow workflow=new ProjectMutationWorkflow(budget,preparation,executor,history);

    @Test void allOperationsShareExistingAdmissionAndRejectBusyWithoutIo(){
        budget.read(()->{
            assertThrows(IllegalStateException.class,()->workflow.capture("p",()->true));
            assertThrows(IllegalStateException.class,()->workflow.prepare(null,()->true));
            assertThrows(IllegalStateException.class,()->workflow.apply(null,"owner",()->true));
            assertThrows(IllegalStateException.class,()->workflow.recover("id","owner",()->true));
            assertThrows(IllegalStateException.class,()->workflow.receipt(null,"owner",()->true));
            assertThrows(IllegalStateException.class,()->workflow.page("p",null,1,()->true));
            assertThrows(IllegalStateException.class,()->workflow.history("p","id",()->true));
            assertEquals(1,budget.status().active());return null;
        },()->true);
        assertEquals(0,budget.status().active());verifyNoInteractions(preparation,executor,history);
    }
    @Test void deadlineReachesInnerPermissionAndReleasesSlot(){
        doAnswer(call->{assertEquals(1,budget.status().active());time.set(1_000_000_001L);
            assertThrows(IllegalStateException.class,()->((BooleanSupplier)call.getArgument(1)).getAsBoolean());return null;
        }).when(preparation).capture(eq("p"),any());
        assertThrows(IllegalStateException.class,()->workflow.capture("p",()->true));assertEquals(0,budget.status().active());
    }
    @Test void permissionLossDuringReadDoesNotReturnResult(){
        var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{allowed.set(false);assertFalse(((BooleanSupplier)call.getArgument(1)).getAsBoolean());return null;}).when(preparation).capture(eq("p"),any());
        assertThrows(SecurityException.class,()->workflow.capture("p",allowed::get));assertEquals(0,budget.status().active());
    }
    @Test void sharedShutdownPreventsMutationAndHistoryWork(){
        budget.close();assertThrows(IllegalStateException.class,()->workflow.capture("p",()->true));
        assertThrows(IllegalStateException.class,()->workflow.history("p","id",()->true));verifyNoInteractions(preparation,executor,history);
    }
}
