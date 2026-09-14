package net.modtale.service.admin.review;

import net.modtale.config.properties.AppReviewRepairProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRepairConfigurationTest {
    String key=Base64.getEncoder().encodeToString(new byte[32]);
    @Test void featureIsAbsentByDefault() {
        new ApplicationContextRunner().withUserConfiguration(ReviewRepairConfiguration.class).run(context->{assertFalse(context.containsBean("reviewRepairPreparation"));assertFalse(context.containsBean("reviewRepairWorkflow"));});
    }
    @Test void enabledFeatureRequiresExplicitKeys() {
        new ApplicationContextRunner().withUserConfiguration(ReviewRepairConfiguration.class).withBean(MongoTemplate.class,()->mock(MongoTemplate.class))
                .withPropertyValues("app.warden.repair.enabled=true").run(context->assertNotNull(context.getStartupFailure()));
    }
    @Test void configuredFeatureConstructsBoundedPreparationWithoutStartingWork() {
        var mongo=mock(MongoTemplate.class,RETURNS_DEEP_STUBS);
        new ApplicationContextRunner().withUserConfiguration(ReviewRepairConfiguration.class).withBean(MongoTemplate.class,()->mongo)
                .withPropertyValues("app.warden.repair.enabled=true","app.warden.repair.active-key=test","app.warden.repair.signing-keys.test="+key)
                .run(context->{assertNull(context.getStartupFailure());assertNotNull(context.getBean(ReviewRepairPreparation.class));assertNotNull(context.getBean(ReviewSnapshotArchive.class));assertNotNull(context.getBean(ReviewRepairWorkflow.class));});
    }
    @Test void invalidKeysAndLimitsFailWithoutEchoingSecretValues() {
        for(String value:List.of("secret",Base64.getEncoder().encodeToString(new byte[31]),key.replace("=",""))) {
            var failure=assertThrows(IllegalArgumentException.class,()->new AppReviewRepairProperties(true,"test",Map.of("test",value),1,1000));
            assertFalse(failure.getMessage().contains(value));
        }
        assertThrows(IllegalArgumentException.class,()->new AppReviewRepairProperties(true,"test",Map.of("test",key),3,1000));
        assertThrows(IllegalArgumentException.class,()->new AppReviewRepairProperties(true,"test",Map.of("test",key),1,900001));
        var properties=new AppReviewRepairProperties(true,"test",Map.of("test",key),1,1000);assertFalse(properties.toString().contains(key));
        var decoded=properties.decodedKeys();decoded.get("test")[0]=7;assertEquals(0,properties.decodedKeys().get("test")[0]);
    }
}
