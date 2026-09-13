package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read-only historical context. Matching reasoning never resolves a current finding. */
@Service
public class PriorFindingReasoningService {
    private final ProjectService projects;
    private final MongoTemplate mongo;
    public PriorFindingReasoningService(ProjectService projects, MongoTemplate mongo) { this.projects=projects;this.mongo=mongo; }
    public record Reasoning(String reviewToken, String sourceVersionId, String sourceVersion, long assessedAt,
            List<String> reviewReasons, List<FindingReviewService.Event> decisions, int omitted) {}
    public Reasoning read(String projectId,String targetId,String sourceId,int issueIndex,String token) {
        var project=projects.getRawProjectById(projectId);
        if(project==null || project.getVersions()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        ProjectReviewSnapshot.requireCurrent(project,token);
        var target=version(project,targetId);var source=version(project,sourceId);
        if(Objects.equals(sourceId,targetId)) throw unavailable();
        var scan=target.getScanResult();
        if(scan==null || scan.getIssues()==null || issueIndex<0 || issueIndex>=scan.getIssues().size()) throw unavailable();
        var issue=scan.getIssues().get(issueIndex);
        String identity=IssueEvidenceIdentity.from(scan).identify(issue);
        String context=ArtifactReviewContext.fingerprint(target);
        if(identity==null || !Objects.equals(target.getHash(),scan.getSecurityEvidence().artifactSha256())
                || context==null || !context.equals(scan.getReviewedContextSha256())) throw unavailable();
        if(source.getFindingReviewHead()==null || ArtifactReviewLineage.extend(project,source)==null) throw unavailable();
        var approved=source.getApprovedSecurityEvidence();
        var events=FindingReviewHistory.load(mongo,projectId,sourceId,source.getFindingReviewHead());
        long now=System.currentTimeMillis();
        var removed=new HashSet<String>();
        for(var event:events) {
            if(!new FindingDecisionValidity().validRecord(event,projectId,source,now)) throw unavailable();
            if(event.revokedDecisionId()!=null) removed.add(event.revokedDecisionId());
            if(event.supersedesDecisionId()!=null) removed.add(event.supersedesDecisionId());
        }
        if(events.stream().anyMatch(e->e.disposition()==FindingReviewService.Disposition.REQUIRE_REVIEW && !removed.contains(e.id()))) throw unavailable();
        boolean approvedOccurrence=source.getApprovedIssueBaselines()!=null && source.getApprovedIssueBaselines().stream()
                .filter(Objects::nonNull).anyMatch(value->identity.equals(value.getEvidenceIdentity()));
        var matched=events.stream().filter(e->approvedOccurrence && e.disposition()==FindingReviewService.Disposition.ACCEPT
                && !removed.contains(e.id()) && "WHOLE_ARTIFACT".equals(e.scope())
                && identity.equals(e.finding().identity()) && Objects.equals(issue.getFilePath(),e.finding().path())
                && Objects.equals(issue.getType(),e.finding().type()) && Objects.equals(issue.getDescription(),e.finding().description())
                && issue.getLineStart()==e.finding().lineStart() && issue.getLineEnd()==e.finding().lineEnd()
                && approved.contentSha256().equals(e.contentSha256()) && approved.policyVersion().equals(e.policyVersion())
                && source.getApprovedSecurityContextSha256().equals(e.contextSha256())
                && e.createdAt()<=source.getSecurityApprovedAt()).toList();
        var reasons=new ArrayList<String>();
        if(!approved.contentSha256().equals(scan.getSecurityEvidence().contentSha256())) reasons.add("Artifact contents changed; callers and resources need current review.");
        if(!source.getApprovedSecurityContextSha256().equals(context)) reasons.add("Runtime, dependency or manifest context changed.");
        if("BLOCK".equals(scan.getVerdict()) || scan.getStatus()==ScanStatus.INFECTED
                || "NEW_SECURITY_EVIDENCE".equals(scan.getSecurityEvidence().reviewState())) reasons.add("Current adverse evidence prevents retaining an acceptance.");
        if(matched.stream().anyMatch(e->e.expiresAt()<=now)) reasons.add("Some earlier acceptances have expired.");
        reasons.add("Historical reasoning only. Current scope, scanner policy and publication requirements still need evaluation.");
        var current=projects.getRawProjectById(projectId);
        if(current==null) throw ProjectReviewSnapshot.conflict();
        ProjectReviewSnapshot.requireCurrent(current,token);
        return new Reasoning(token,sourceId,source.getVersionNumber(),now,List.copyOf(reasons),matched.stream().limit(20).toList(),Math.max(0,matched.size()-20));
    }
    private ProjectVersion version(Project project,String id) {
        var versions=project.getVersions().stream().filter(Objects::nonNull).filter(v->Objects.equals(id,v.getId())).toList();
        if(id==null || versions.size()!=1) throw unavailable();return versions.getFirst();
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT,"Verified prior reasoning is unavailable for this evidence and source approval. Refresh the review or inspect source history.");
    }
}
