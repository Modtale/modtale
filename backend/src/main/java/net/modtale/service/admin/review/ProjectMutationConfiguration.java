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
    @Bean ProjectMutationOwnerAuthority projectMutationOwnerAuthority(net.modtale.service.user.account.AccountService accounts,
            net.modtale.service.security.access.AccessControlService access,net.modtale.repository.user.ApiKeyRepository keys,MongoTemplate mongo) {
        return new ProjectMutationOwnerAuthority(accounts,access,keys,mongo);
    }

    @Bean ProjectMutationOwnerAccess projectMutationOwnerAccess(ReviewRepairWorkflow budget,ProjectMutationOwnerAuthority authority,
            ProjectMutationPreparation preparation,ProjectMutationExecutor executor,ProjectMutationReferenceReader history) {
        return new ProjectMutationOwnerAccess(budget,authority,preparation,executor,history);
    }

    @Bean ProjectMutationPriorWorkReader projectMutationPriorWorkReader(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history) {
        return new ProjectMutationPriorWorkReader(mongo,budget,history);
    }

    @Bean
    @ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
    ProjectMutationAdmissionPreparation projectMutationAdmissionPreparation(MongoTemplate mongo,ReviewRepairWorkflow budget,ReviewSnapshotArchive archive,ProjectMutationReferenceReader history,ProjectMutationPriorWorkReader prior,ProjectMutationJobAccounting accounting,net.modtale.service.security.scan.RemoteReviewClient client,AppReviewRepairProperties properties) {
        return new ProjectMutationAdmissionPreparation(mongo,budget,archive,history,prior,accounting,client,Clock.systemUTC(),Math.min(120000,properties.preparationLifetimeMillis()));
    }

    @Bean
    @ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
    ProjectMutationActivator projectMutationActivator(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationAdmissionPreparation preparation,ReviewSnapshotArchive archive,ReviewRepairJournal journal) {
        return new ProjectMutationActivator(mongo,budget,preparation,archive,journal);
    }

    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="app.warden.jobs.enabled",havingValue="true")
    ProjectMutationJobAccounting projectMutationJobAccounting(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history,
            net.modtale.service.security.scan.RemoteReviewClient client,AppReviewRepairProperties properties) {
        return new ProjectMutationJobAccounting(mongo,budget,history,client,properties.concurrency());
    }

}
