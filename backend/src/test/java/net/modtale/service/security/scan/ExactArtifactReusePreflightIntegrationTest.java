package net.modtale.service.security.scan;

import com.mongodb.client.*;
import java.util.*;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ExactArtifactReusePreflightIntegrationTest {
    MongoClient db;MongoTemplate mongo;String database;
    String projectId="abcdefabcdefabcdefabcdef",requestId=UUID.randomUUID().toString();
    ProjectRepository projects;StorageService storage;WardenClientService scanner;ExactArtifactReusePreflight preflight;
    ScanResult candidate;

    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");
        if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException("Unexpected test database port");
        db=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000");
        database="exact_reuse_preflight_"+UUID.randomUUID().toString().replace("-","");
        var factory=new org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory(db,database);
        var conversions=new net.modtale.config.db.MongoConfig().mongoCustomConversions(new net.modtale.config.db.MongoArtifactManifestStore(factory));
        var context=new org.springframework.data.mongodb.core.mapping.MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());context.afterPropertiesSet();
        var converter=new org.springframework.data.mongodb.core.convert.MappingMongoConverter(new org.springframework.data.mongodb.core.convert.DefaultDbRefResolver(factory),context);
        converter.setCustomConversions(conversions);converter.afterPropertiesSet();mongo=new MongoTemplate(factory,converter);

        candidate=ScanEvidenceFixtures.complete(false);var evidence=candidate.getSecurityEvidence();
        candidate.setSecurityEvidence(new ScanResult.SecurityEvidence(evidence.policyVersion(),evidence.artifactSha256(),
                evidence.contentSha256(),true,false,"SCANNER_EVIDENCE_ONLY",evidence.entryHashes()));
        var source=new ProjectVersion();source.setId("source");source.setVersionNumber("1.0");source.setHash(evidence.artifactSha256());
        source.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);source.setApprovedSecurityEvidence(evidence);
        source.setSecurityApprovalProjectId(projectId);source.setSecurityApprovedAt(System.currentTimeMillis()-1000);
        source.setApprovedReviewOrigins(Map.of());source.setApprovedSecurityContextSha256(ArtifactReviewContext.fingerprint(source));
        var target=new ProjectVersion();target.setId("target");target.setVersionNumber("1.1");target.setHash(evidence.artifactSha256());
        target.setFileUrl("original.zip");target.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        var queued=new ScanResult();queued.setStatus(ScanStatus.SCANNING);queued.setScanState("QUEUED");
        queued.setScanAttempt(1);queued.setScanRequestId(requestId);target.setScanResult(queued);
        var project=new Project();project.setId(projectId);project.setVersions(List.of(source,target));mongo.insert(project);

        projects=mock(ProjectRepository.class);when(projects.findById(projectId)).thenAnswer(i->Optional.ofNullable(mongo.findById(projectId,Project.class)));
        storage=mock(StorageService.class);when(storage.downloadBounded(eq("original.zip"),anyInt())).thenReturn("original artifact".getBytes());
        scanner=mock(WardenClientService.class);when(scanner.currentPolicyVersion()).thenReturn(evidence.policyVersion());
        when(scanner.scanEvidenceFile(any(),eq("original.zip"))).thenReturn(candidate);
        var projectService=mock(ProjectService.class);var issues=mock(SecurityIssueAnalysisService.class);
        when(issues.collectApprovedIssueBaselines(any(),any())).thenReturn(new SecurityIssueAnalysisService.BaselineIndex(Map.of(),Map.of()));
        when(issues.annotateAgainstBaselines(any(),any())).thenReturn(new SecurityIssueAnalysisService.ClassificationStats(0,0,0,false));
        var persistence=new ScanPersistenceService(mongo,projects,projectService);
        var completion=new ScanCompletionService(projects,projectService,mock(ProjectNotificationService.class),mock(WebhookService.class),
                issues,new ScanRoutingService(new AppSecurityProperties("fixture",60,120,2,4,15,20,25,2)),persistence,
                new ProjectVersionAccessService(null),scanner);
        preflight=new ExactArtifactReusePreflight(projects,storage,scanner,persistence,completion,new ArtifactReviewReuseService());
    }
    @AfterEach void cleanup() {if(db!=null){db.getDatabase(database).drop();db.close();}}
    ProjectVersion target(){return mongo.findById(projectId,Project.class).getVersions().get(1);}
    @Test void exactApprovalUsesFreshScannerEvidenceAndSchedulesWithoutReviewJob() {
        assertTrue(preflight.tryComplete(projectId,target(),candidate.getSecurityEvidence().policyVersion()));
        var saved=target();
        assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,saved.getReviewStatus());
        assertEquals("1.0",saved.getScanResult().getReusedReviewVersion());
        assertEquals("SCANNER_EVIDENCE_ONLY",saved.getScanResult().getSecurityEvidence().reviewState());
        assertFalse(saved.getScanResult().getSecurityEvidence().clearanceGranted());
        assertNull(saved.getScanResult().getRemoteReview());
        verify(scanner,times(1)).scanEvidenceFile(any(),eq("original.zip"));
    }
    @Test void sourceRevokedDuringFreshScanCannotCompleteReuse() {
        when(scanner.scanEvidenceFile(any(),eq("original.zip"))).thenAnswer(i->{
            mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)),
                    new Update().set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.REJECTED),Project.class);
            return candidate;
        });
        assertFalse(preflight.tryComplete(projectId,target(),candidate.getSecurityEvidence().policyVersion()));
        assertEquals("QUEUED",target().getScanResult().getScanState());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,target().getReviewStatus());
    }
    @Test void sourceRevokedAfterPreflightCannotPersistScannerOnlyHoldAsACompletedReview() {
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i->{
            if(reads.incrementAndGet()==3)mongo.updateFirst(Query.query(Criteria.where("_id").is(projectId)),
                    new Update().set("versions.0.reviewStatus",ProjectVersion.ReviewStatus.REJECTED),Project.class);
            return Optional.ofNullable(mongo.findById(projectId,Project.class));
        }).when(projects).findById(projectId);
        assertFalse(preflight.tryComplete(projectId,target(),candidate.getSecurityEvidence().policyVersion()));
        assertEquals("SCANNING",target().getScanResult().getScanState());
        assertNull(target().getScanResult().getReusedReviewVersion());
        assertEquals(ProjectVersion.ReviewStatus.PENDING,target().getReviewStatus());
    }
}
