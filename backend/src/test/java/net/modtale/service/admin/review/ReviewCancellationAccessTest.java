package net.modtale.service.admin.review;

import net.modtale.model.user.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCancellationAccessTest {
    AccountService accounts;ReviewRepairWorkflow workflow;ReviewOrphanCancellationJournal journal;
    ReviewOrphanCancellationExecutor executor;ReviewCancellationReconciler reconciler;ReviewCancellationAccess access;User actor;
    AtomicLong time=new AtomicLong();
    ReviewOrphanCancellationJournal.Prepared original=new ReviewOrphanCancellationJournal.Prepared(UUID.randomUUID().toString(),"a".repeat(64),1,1001);
    ReviewCancellationAccess.Check check=new ReviewCancellationAccess.Check(UUID.randomUUID().toString(),original);
    @BeforeEach void setup() {
        accounts=mock(AccountService.class);journal=mock(ReviewOrphanCancellationJournal.class);executor=mock(ReviewOrphanCancellationExecutor.class);reconciler=mock(ReviewCancellationReconciler.class);
        workflow=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1,1000,time::get);
        access=new ReviewCancellationAccess(accounts,workflow,journal,executor,reconciler);actor=ReviewRepairAccessTest.user("actor");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        when(accounts.getCurrentUser(any())).thenReturn(actor);
    }
    @AfterEach void cleanup(){workflow.close();SecurityContextHolder.clearContext();}
    @Test void allOperationsRequireBothPermissionsBeforeValidationOrWork() {
        for(var permissions:List.of(Set.of(AdminPermission.PROJECT_REVIEW_READ),Set.of(AdminPermission.PROJECT_VERSION_RESCAN),Set.of(AdminPermission.PROJECT_REVIEW_DECIDE))) {
            actor.setAdminPermissions(permissions);
            assertThrows(SecurityException.class,()->access.recover(null));assertThrows(SecurityException.class,()->access.prepare(null));assertThrows(SecurityException.class,()->access.execute(null));
            assertThrows(SecurityException.class,()->access.receipt(null));assertThrows(SecurityException.class,()->access.check(null));assertThrows(SecurityException.class,()->access.checkReceipt(null));
        }
        verifyNoInteractions(journal,executor,reconciler);
    }
    @Test void deletedAndMissingAccountsHaveNoAuthority() {
        actor.setDeletedAt(java.time.LocalDateTime.now());assertThrows(SecurityException.class,()->access.execute(original));
        when(accounts.getCurrentUser(any())).thenReturn(null);assertThrows(SecurityException.class,()->access.check(check));verifyNoInteractions(journal,executor,reconciler);
    }
    @Test void malformedRequestsNeverReachComponents() {
        assertThrows(IllegalArgumentException.class,()->access.recover("wrong"));assertThrows(IllegalArgumentException.class,()->access.prepare("wrong"));
        assertThrows(IllegalArgumentException.class,()->access.execute(new ReviewOrphanCancellationJournal.Prepared(original.isolationId(),original.targetSha256(),1,120002)));
        assertThrows(IllegalArgumentException.class,()->access.receipt(new ReviewOrphanCancellationJournal.Prepared(original.isolationId(),"A".repeat(64),1,2)));
        assertThrows(IllegalArgumentException.class,()->access.check(new ReviewCancellationAccess.Check("bad",original)));
        assertThrows(IllegalArgumentException.class,()->access.checkReceipt(new ReviewCancellationAccess.Check(check.id(),null)));
        verifyNoInteractions(journal,executor,reconciler);
    }
    @Test void preparationUsesServerActorAndDoesNotDispatch() {
        when(journal.prepare(eq(original.isolationId()),eq("actor"),any())).thenAnswer(i->{assertTrue(i.<BooleanSupplier>getArgument(2).getAsBoolean());return original;});
        assertEquals(original,access.prepare(original.isolationId()));verifyNoInteractions(executor,reconciler);
    }
    @Test void executeRechecksAccountIdentityAndRefusesResponseAfterSwitch() {
        when(executor.execute(eq(original),eq("actor"),any())).thenAnswer(i->{
            var allowed=i.<BooleanSupplier>getArgument(2);assertTrue(allowed.getAsBoolean());
            when(accounts.getCurrentUser(any())).thenReturn(ReviewRepairAccessTest.user("other"));assertFalse(allowed.getAsBoolean());
            return new ReviewOrphanCancellationExecutor.Execution("UNKNOWN",null);
        });
        assertThrows(SecurityException.class,()->access.execute(original));assertEquals(0,workflow.status().active());
    }
    @Test void sharedDeadlineIsVisibleInsideRemoteWorkAndAdmissionRemainsUntilReturn() {
        when(reconciler.check(eq(check.id()),eq(original),eq("actor"),any())).thenAnswer(i->{
            assertEquals(1,workflow.status().active());time.set(TimeUnit.SECONDS.toNanos(1));
            assertThrows(IllegalStateException.class,()->i.<BooleanSupplier>getArgument(3).getAsBoolean());
            assertEquals(1,workflow.status().overdue());return null;
        });
        assertThrows(IllegalStateException.class,()->access.check(check));assertEquals(0,workflow.status().active());
    }
    @Test void nestedWorkCannotBypassSharedAdmission() {
        when(journal.prepare(anyString(),anyString(),any())).thenAnswer(i->{assertThrows(IllegalStateException.class,()->access.check(check));return original;});
        assertEquals(original,access.prepare(original.isolationId()));verifyNoInteractions(reconciler);
    }
    @Test void closureIsObservedInsideDispatchAndPreventsNewCalls() {
        when(executor.execute(any(),anyString(),any())).thenAnswer(i->{workflow.close();assertThrows(IllegalStateException.class,()->i.<BooleanSupplier>getArgument(2).getAsBoolean());return null;});
        assertThrows(IllegalStateException.class,()->access.execute(original));assertEquals("CLOSED",workflow.status().state());
        assertThrows(IllegalStateException.class,()->access.prepare(original.isolationId()));verifyNoInteractions(journal);
    }
    @Test void recoveryUsesOnlyKnownIsolationIdentityAndNeverPreparesOrDispatches() {
        var expected=new ReviewOrphanCancellationJournal.Receipt(original,"PREPARED",null,null);
        when(journal.recover(eq(original.isolationId()),eq("actor"),any())).thenAnswer(i->{assertTrue(i.<BooleanSupplier>getArgument(2).getAsBoolean());return expected;});
        assertEquals(expected,access.recover(original.isolationId()));
        verify(journal).recover(eq(original.isolationId()),eq("actor"),any());verifyNoMoreInteractions(journal);verifyNoInteractions(executor,reconciler);
    }
    @Test void localReceiptLookupsNeverInvokeRemoteOperationsOrRenewPreparation() {
        var receipt=new ReviewOrphanCancellationJournal.Receipt(original,"UNKNOWN",null,2L);
        var later=new ReviewCancellationReconciler.Receipt(check.id(),original,"READING",null,null);
        when(journal.receipt(eq(original),eq("actor"),any())).thenReturn(receipt);
        when(reconciler.receipt(eq(check.id()),eq(original),eq("actor"),any())).thenReturn(later);
        assertEquals(receipt,access.receipt(original));assertEquals(later,access.checkReceipt(check));
        verify(journal).receipt(eq(original),eq("actor"),any());verify(reconciler).receipt(eq(check.id()),eq(original),eq("actor"),any());
        verifyNoMoreInteractions(journal,reconciler);verifyNoInteractions(executor);
    }
}
