package net.modtale.service.admin.review;

import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import org.bson.types.ObjectId;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.function.BooleanSupplier;

/** Account-bound boundary for the disabled-by-default moderator repair workflow. */
public final class ReviewRepairAccess {
    public record Position(String projectIdType,String projectId,int versionIndex) {}
    public record Inspect(Position position,String versionId) {}
    public record Preview(Position position,String versionId,String sha256,boolean eligible,String effect) {}
    public record Prepare(String id,Position position,String versionId,String expectedSha256) {}
    private final AccountService accounts;
    private final ReviewRepairWorkflow workflow;
    private final RawReviewSnapshotReader reader;
    private final ReviewIsolationExecutor isolation;
    public ReviewRepairAccess(AccountService accounts,ReviewRepairWorkflow workflow,RawReviewSnapshotReader reader,ReviewIsolationExecutor isolation) {
        this.accounts=accounts;this.workflow=workflow;this.reader=reader;this.isolation=isolation;
    }
    public Preview inspect(Inspect request) {
        var authority=authority();if(request==null)throw invalid();Object project=project(request.position());
        return workflow.read(()->{
            var captured=reader.capture(project,request.position().versionIndex(),request.versionId());
            return new Preview(request.position(),captured.versionId(),captured.sha256(),isolation.eligible(captured),"ISOLATE_LOCAL_REVIEW");
        },authority.allowed());
    }
    public ReviewRepairPreparation.Prepared prepare(Prepare request) {
        var authority=authority();if(request==null)throw invalid();Object project=project(request.position());
        var input=new ReviewRepairPreparation.Request(request.id(),project,request.position().versionIndex(),request.versionId(),request.expectedSha256(),authority.actor(),ReviewSnapshotArchive.Action.ISOLATE_REVIEW);
        return workflow.prepare(input,authority.allowed());
    }
    public ReviewIsolationExecutor.Result execute(ReviewRepairPreparation.Prepared prepared) {
        var authority=authority();validate(prepared);return workflow.isolate(prepared,authority.actor(),authority.allowed());
    }
    public ReviewIsolationExecutor.Result receipt(ReviewRepairPreparation.Prepared prepared) {
        var authority=authority();validate(prepared);return workflow.read(()->isolation.receipt(prepared,authority.actor()),authority.allowed());
    }
    private record Authority(String actor,BooleanSupplier allowed) {}
    private Authority authority() {
        User user=current();if(!allowed(user))throw new SecurityException("Review repair is not permitted");
        String actor=user.getId();
        return new Authority(actor,()->{User refreshed=current();return allowed(refreshed)&&actor.equals(refreshed.getId());});
    }
    private User current(){return accounts.getCurrentUser(SecurityContextHolder.getContext().getAuthentication());}
    private static boolean allowed(User user) {
        return user!=null && !user.isDeleted() && user.getId()!=null && !user.getId().isBlank()
                && AdminPermission.hasPermission(user,AdminPermission.PROJECT_REVIEW_READ)
                && AdminPermission.hasPermission(user,AdminPermission.PROJECT_VERSION_RESCAN);
    }
    private static Object project(Position position) {
        if(position==null || position.projectId()==null || position.versionIndex()<0 || position.versionIndex()>16*1024*1024)throw invalid();
        if("OBJECT_ID".equals(position.projectIdType())) {
            if(!position.projectId().matches("[0-9a-f]{24}"))throw invalid();return new ObjectId(position.projectId());
        }
        if(!"STRING".equals(position.projectIdType()) || position.projectId().isEmpty() || position.projectId().length()>128
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(position.projectId()))throw invalid();
        return position.projectId();
    }
    private static void validate(ReviewRepairPreparation.Prepared prepared) {
        if(prepared==null || prepared.id()==null || !prepared.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || prepared.sha256()==null || !prepared.sha256().matches("[0-9a-f]{64}") || prepared.createdAt()<=0 || prepared.expiresAt()<=prepared.createdAt()
                || prepared.expiresAt()-prepared.createdAt()>900000)throw invalid();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid review repair request");}
}
