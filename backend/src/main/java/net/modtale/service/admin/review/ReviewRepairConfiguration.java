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
    @Bean RawReviewSnapshotReader rawReviewSnapshotReader(MongoTemplate mongo){return new RawReviewSnapshotReader(mongo);}
    @Bean ReviewRepairPreparation reviewRepairPreparation(ReviewSnapshotArchive archive,RawReviewSnapshotReader reader,AppReviewRepairProperties properties) {
        return new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),properties.concurrency(),properties.preparationLifetimeMillis());
    }
}
