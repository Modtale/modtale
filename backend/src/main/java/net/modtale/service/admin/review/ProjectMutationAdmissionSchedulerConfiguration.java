package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewScheduler;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="app.warden.repair.admission.scheduler.enabled",havingValue="true")
public class ProjectMutationAdmissionSchedulerConfiguration {
    @Bean(destroyMethod="close")
    ProjectMutationAdmissionScheduler projectMutationAdmissionScheduler(ProjectMutationDiscovery discovery,ProjectMutationAutomaticAdmission automatic,
            RemoteReviewScheduler delivery,
            @Value("${app.warden.repair.enabled:false}") boolean repair,
            @Value("${app.warden.jobs.enabled:false}") boolean jobs,
            @Value("${app.warden.repair.admission.scheduler.workers:1}") int workers,
            @Value("${app.warden.repair.admission.scheduler.page-size:16}") int pageSize,
            @Value("${app.warden.repair.admission.scheduler.poll-millis:1000}") long poll,
            @Value("${app.warden.repair.admission.scheduler.drain-millis:10000}") long drain) {
        if(!repair || !jobs)throw new IllegalStateException("Automatic admission requires repair and remote review enabled");
        java.util.Objects.requireNonNull(delivery);
        return new ProjectMutationAdmissionScheduler(discovery,automatic,new ProjectMutationAdmissionScheduler.Settings(workers,pageSize,poll,drain));
    }
}
