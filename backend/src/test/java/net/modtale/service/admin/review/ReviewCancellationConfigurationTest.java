package net.modtale.service.admin.review;

import net.modtale.config.properties.AppReviewRepairProperties;
import net.modtale.service.security.scan.RemoteReviewClient;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCancellationConfigurationTest {
    ApplicationContextRunner runner(){return new ApplicationContextRunner().withUserConfiguration(ReviewCancellationConfiguration.class);}
    @Test void disabledConfigurationCreatesNoComponentsEvenWithOtherFlagsEnabled() {
        runner().withPropertyValues("app.warden.repair.enabled=true","app.warden.jobs.enabled=true").run(c->{assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(ReviewCancellationAccess.class).size());});
    }
    @Test void enablingWithoutRequiredRepairAndRemoteDependenciesFailsStartup() {
        runner().withPropertyValues("app.warden.repair.cancellation.enabled=true").run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void enabledComponentsShareWorkflowAndCloseOnContextDestruction() {
        var mongo=mock(MongoTemplate.class);@SuppressWarnings("unchecked") MongoCollection<Document> collection=mock(MongoCollection.class,RETURNS_SELF);
        when(mongo.getCollection(anyString())).thenReturn(collection);
        var workflow=mock(ReviewRepairWorkflow.class);var executor=new ReviewOrphanCancellationExecutor[1];var reconciler=new ReviewCancellationReconciler[1];
        runner().withPropertyValues("app.warden.repair.cancellation.enabled=true","app.warden.jobs.enabled=true")
                .withBean(MongoTemplate.class,()->mongo).withBean(ReviewSnapshotArchive.class,()->mock(ReviewSnapshotArchive.class))
                .withBean(ReviewIsolationExecutor.class,()->mock(ReviewIsolationExecutor.class)).withBean(ReviewRepairWorkflow.class,()->workflow)
                .withBean(AccountService.class,()->mock(AccountService.class)).withBean(RemoteReviewClient.class,()->mock(RemoteReviewClient.class))
                .withBean(AppReviewRepairProperties.class,()->new AppReviewRepairProperties(false,"",Map.of(),1,300000))
                .run(c->{assertNull(c.getStartupFailure());assertNotNull(c.getBean(ReviewCancellationAccess.class));executor[0]=c.getBean(ReviewOrphanCancellationExecutor.class);reconciler[0]=c.getBean(ReviewCancellationReconciler.class);verifyNoInteractions(workflow);});
        assertEquals("SHUTDOWN",executor[0].execute(null,"actor",()->true).state());
        assertThrows(IllegalStateException.class,()->reconciler[0].check("11111111-1111-1111-1111-111111111111",null,"actor",()->true));
    }
}
