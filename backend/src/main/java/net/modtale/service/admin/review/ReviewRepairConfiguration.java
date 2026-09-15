package net.modtale.service.admin.review;

import net.modtale.config.properties.AppReviewRepairProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.warden.repair.enabled",havingValue="true")
@EnableConfigurationProperties(AppReviewRepairProperties.class)
public class ReviewRepairConfiguration {
    @Bean ReviewSnapshotArchive reviewSnapshotArchive(MongoTemplate mongo,AppReviewRepairProperties properties) {
        return new ReviewSnapshotArchive(mongo,properties.activeKey(),properties.decodedKeys());
    }
    @Bean ReviewRepairJournal reviewRepairJournal(MongoTemplate mongo,ReviewSnapshotArchive archive){return new ReviewRepairJournal(mongo,archive);}
    @Bean(initMethod="initializeDiscovery") ReviewIsolationExecutor reviewIsolationExecutor(MongoTemplate mongo,ReviewSnapshotArchive archive,RawReviewSnapshotReader reader,ReviewRepairJournal journal) {
        return new ReviewIsolationExecutor(mongo,archive,reader,journal);
    }
    @Bean ReviewRepairWorkflow reviewRepairWorkflow(ReviewRepairPreparation preparation,ReviewIsolationExecutor isolation,AppReviewRepairProperties properties) {
        return new ReviewRepairWorkflow(preparation,isolation,properties.concurrency());
    }
    @Bean ReviewRepairAccess reviewRepairAccess(net.modtale.service.user.account.AccountService accounts,ReviewRepairWorkflow workflow,RawReviewSnapshotReader reader,ReviewIsolationExecutor isolation) {
        return new ReviewRepairAccess(accounts,workflow,reader,isolation);
    }
    @Bean RawReviewSnapshotReader rawReviewSnapshotReader(MongoTemplate mongo){return new RawReviewSnapshotReader(mongo);}
    @Bean ReviewRepairPreparation reviewRepairPreparation(ReviewSnapshotArchive archive,RawReviewSnapshotReader reader,AppReviewRepairProperties properties) {
        return new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),properties.concurrency(),properties.preparationLifetimeMillis());
    }
}
