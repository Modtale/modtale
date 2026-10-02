package net.modtale.service.security.scan;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name="app.warden.jobs.scheduler.enabled",havingValue="true")
public class RemoteReviewSchedulerConfig {
    @Bean(destroyMethod="close")
    RemoteReviewScheduler remoteReviewScheduler(RemoteReviewDiscovery discovery,ObjectProvider<RemoteReviewBootstrap> bootstrap,
            @Value("${app.warden.jobs.enabled:false}") boolean enabled,
            @Value("${app.warden.jobs.scheduler.workers:2}") int workers,
            @Value("${app.warden.jobs.scheduler.page-size:16}") int pageSize,
            @Value("${app.warden.jobs.scheduler.poll-millis:1000}") long poll,
            @Value("${app.warden.jobs.scheduler.drain-millis:10000}") long drain) {
        if(!enabled)throw new IllegalStateException("Remote scheduler requires remote jobs enabled");
        return new RemoteReviewScheduler(discovery,bootstrap.getObject(),new RemoteReviewScheduler.Settings(workers,pageSize,poll,drain));
    }
}
