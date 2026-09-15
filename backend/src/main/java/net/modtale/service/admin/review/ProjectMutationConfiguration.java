package net.modtale.service.admin.review;

import net.modtale.config.properties.AppReviewRepairProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.warden.repair.enabled",havingValue="true")
public class ProjectMutationConfiguration {
    @Bean ProjectMutationPreparation projectMutationPreparation(MongoTemplate mongo,ReviewSnapshotArchive archive,AppReviewRepairProperties properties) {
        return new ProjectMutationPreparation(mongo,archive,Clock.systemUTC(),Math.min(120000,properties.preparationLifetimeMillis()));
    }
    @Bean ProjectMutationExecutor projectMutationExecutor(MongoTemplate mongo,ProjectMutationPreparation preparation,ReviewSnapshotArchive archive,ReviewRepairJournal journal) {
        return new ProjectMutationExecutor(mongo,preparation,archive,journal);
    }
    @Bean(initMethod="initialize") ProjectMutationReferenceReader projectMutationReferenceReader(MongoTemplate mongo,ReviewSnapshotArchive archive,ProjectMutationPreparation preparation,ProjectMutationExecutor executor) {
        return new ProjectMutationReferenceReader(mongo,archive,preparation,executor);
    }
    @Bean ProjectMutationWorkflow projectMutationWorkflow(ReviewRepairWorkflow budget,ProjectMutationPreparation preparation,ProjectMutationExecutor executor,ProjectMutationReferenceReader history) {
        return new ProjectMutationWorkflow(budget,preparation,executor,history);
    }
}
