package net.modtale.service.admin.review;

import net.modtale.model.user.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRepairAccessTest {
    AccountService accounts;ReviewRepairWorkflow workflow;RawReviewSnapshotReader reader;ReviewIsolationExecutor isolation;ReviewRepairAccess access;User actor;
    ReviewRepairAccess.Position position=new ReviewRepairAccess.Position("STRING","p",0);
    @BeforeEach void setup() {
        accounts=mock(AccountService.class);workflow=mock(ReviewRepairWorkflow.class);reader=mock(RawReviewSnapshotReader.class);isolation=mock(ReviewIsolationExecutor.class);
        access=new ReviewRepairAccess(accounts,workflow,reader,isolation);actor=user("actor");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        when(accounts.getCurrentUser(any())).thenReturn(actor);
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    static User user(String id){var user=new User();user.setId(id);user.setAdminPermissions(Set.of(AdminPermission.PROJECT_REVIEW_READ,AdminPermission.PROJECT_VERSION_RESCAN));return user;}
    @Test void neitherReadNorRescanAloneAuthorizesAnyRepairOperation() {
        var prepared=new ReviewRepairPreparation.Prepared(UUID.randomUUID().toString(),"a".repeat(64),1,2);
        for(var permissions:List.of(Set.of(AdminPermission.PROJECT_REVIEW_READ),Set.of(AdminPermission.PROJECT_VERSION_RESCAN),Set.of(AdminPermission.PROJECT_REVIEW_DECIDE))) {
            actor.setAdminPermissions(permissions);
            assertThrows(SecurityException.class,()->access.inspect(null));assertThrows(SecurityException.class,()->access.prepare(null));
            assertThrows(SecurityException.class,()->access.execute(prepared));assertThrows(SecurityException.class,()->access.receipt(prepared));
        }
        verifyNoInteractions(workflow,reader,isolation);
    }
    @Test void preparationUsesServerActorAndRechecksFreshAccountIdentity() {
        var id=UUID.randomUUID().toString();
        when(workflow.prepare(any(),any())).thenAnswer(i->{
            var input=i.<ReviewRepairPreparation.Request>getArgument(0);assertEquals("actor",input.actorId());assertEquals(ReviewSnapshotArchive.Action.ISOLATE_REVIEW,input.action());
            var allowed=i.<BooleanSupplier>getArgument(1);assertTrue(allowed.getAsBoolean());
            when(accounts.getCurrentUser(any())).thenReturn(user("other"));assertFalse(allowed.getAsBoolean());
            return new ReviewRepairPreparation.Prepared(id,"a".repeat(64),1,2);
        });
        assertEquals(id,access.prepare(new ReviewRepairAccess.Prepare(id,position,"v","a".repeat(64))).id());
    }
    @Test void deletedOrMissingAccountsCannotReachWorkflow() {
        actor.setDeletedAt(java.time.LocalDateTime.now());assertThrows(SecurityException.class,()->access.inspect(null));
        when(accounts.getCurrentUser(any())).thenReturn(null);assertThrows(SecurityException.class,()->access.inspect(null));verifyNoInteractions(workflow);
    }
    @Test void malformedIdentityAndIntentAreRejectedBeforeWork() {
        for(var p:List.of(new ReviewRepairAccess.Position("OBJECT_ID","ABCDEFABCDEFABCDEFABCDEF",0),new ReviewRepairAccess.Position("NUMBER","1",0),new ReviewRepairAccess.Position("STRING","p",-1)))
            assertThrows(IllegalArgumentException.class,()->access.inspect(new ReviewRepairAccess.Inspect(p,"v")));
        assertThrows(IllegalArgumentException.class,()->access.execute(new ReviewRepairPreparation.Prepared("invalid","a".repeat(64),1,2)));
        assertThrows(IllegalArgumentException.class,()->access.receipt(new ReviewRepairPreparation.Prepared(UUID.randomUUID().toString(),"a".repeat(64),2,1)));verifyNoInteractions(workflow,reader,isolation);
    }
}
