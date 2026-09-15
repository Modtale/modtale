package net.modtale.service.admin.review;

import net.modtale.config.properties.AppReviewRepairProperties;
import net.modtale.service.security.scan.RemoteReviewClient;
import net.modtale.service.user.account.AccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.warden.repair.cancellation.enabled",havingValue="true")
public class ReviewCancellationConfiguration {
    @Bean ReviewOrphanTargetResolver reviewOrphanTargetResolver(MongoTemplate mongo,ReviewSnapshotArchive archive,ReviewIsolationExecutor isolation) {
        return new ReviewOrphanTargetResolver(mongo,archive,isolation);
    }
    @Bean ReviewOrphanCancellationJournal reviewOrphanCancellationJournal(MongoTemplate mongo,ReviewOrphanTargetResolver resolver,ReviewSnapshotArchive archive) {
        return new ReviewOrphanCancellationJournal(mongo,resolver,archive,Clock.systemUTC(),60000);
    }
    @Bean ReviewOrphanCancellationExecutor reviewOrphanCancellationExecutor(ReviewOrphanCancellationJournal journal,RemoteReviewClient client,AppReviewRepairProperties properties) {
        return new ReviewOrphanCancellationExecutor(journal,client,properties.concurrency(),Duration.ofSeconds(30));
    }
    @Bean ReviewCancellationReconciler reviewCancellationReconciler(MongoTemplate mongo,ReviewOrphanCancellationJournal journal,RemoteReviewClient client,AppReviewRepairProperties properties) {
        return new ReviewCancellationReconciler(mongo,journal,client,properties.concurrency(),Duration.ofSeconds(30));
    }
    @Bean ReviewCancellationAccess reviewCancellationAccess(AccountService accounts,ReviewRepairWorkflow workflow,ReviewOrphanCancellationJournal journal,
            ReviewOrphanCancellationExecutor executor,ReviewCancellationReconciler reconciler) {
        return new ReviewCancellationAccess(accounts,workflow,journal,executor,reconciler);
    }
}
