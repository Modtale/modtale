package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import net.modtale.model.project.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationBackgroundFlowTest {
    ProjectMutationAutomaticAdmissionTest base=new ProjectMutationAutomaticAdmissionTest();
    @BeforeEach void setup()throws Exception{base.setup();}
    @AfterEach void cleanup(){base.cleanup();}
    @Test void backgroundAdmissionAndDeliveryCompleteWithoutAnUploadEnqueue()throws Exception {
        var f=base.f();f.server.removeContext("/api/v1/review-jobs");var created=new AtomicBoolean();
        f.route(e->{String path=e.getRequestURI().getPath();if(path.endsWith("/configuration")){
            byte[] body=f.mapper.writeValueAsBytes(Map.of("policyVersion",f.binding.policyVersion(),"reviewConfigSha256",f.binding.reviewConfigSha256()));e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);
        }else if(e.getRequestMethod().equals("POST")){
            f.binding=f.mongo.findById(f.project,Project.class).getVersions().get(1).getScanResult().getRemoteReview();f.job=UUID.randomUUID().toString();created.set(true);f.reply(e,202,"QUEUED");
        }else if(path.contains("/requests/")&&!created.get())f.reply(e,404,"QUEUED");else f.reply(e,200,"COMPLETED","COMPLETED");});
        var bootstrap=new RemoteReviewBootstrap(new RemoteReviewPersistence(f.mongo),f.client,f.step);
        try(var delivery=new RemoteReviewScheduler(new RemoteReviewDiscovery(f.mongo),bootstrap,new RemoteReviewScheduler.Settings(1,4,100,1000));
            var admission=new ProjectMutationAdmissionScheduler(new ProjectMutationDiscovery(f.mongo),base.automatic,new ProjectMutationAdmissionScheduler.Settings(1,4,100,1000))) {
            delivery.start();admission.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);ProjectVersion version=null;
            while(System.nanoTime()<end){version=f.mongo.findById(f.project,Project.class).getVersions().get(1);if(version.getReviewStatus()==ProjectVersion.ReviewStatus.SCHEDULED)break;Thread.sleep(50);}
            assertNotNull(version);assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,version.getReviewStatus());assertEquals(1,f.posts.get());assertNotNull(version.getScanResult().getSecurityEvidence());
            assertEquals(f.binding.withJobId(f.job),version.getScanResult().getRemoteReview());assertTrue(admission.status().processed()>=1);assertEquals(0,admission.unavailableCandidates());assertEquals(0,admission.status().failures());assertEquals(0,delivery.status().failures());
        }
        assertEquals(1,f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());
    }
}
