package net.modtale.service.security.issue;

import java.time.Instant;
import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.admin.review.VersionReviewPersistence;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.ArtifactReviewContext;
import net.modtale.service.security.scan.ArtifactClearancePolicy;
import net.modtale.service.security.scan.WardenClientService;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FindingReviewService {
    public static final String COLLECTION = "finding_review_events";
    private static final int MAX_EVENTS = 1000;
    private final MongoTemplate mongo;
    private final VersionReviewPersistence persistence;
    private final ProjectService projects;
    private final WardenClientService warden;

    public FindingReviewService(MongoTemplate mongo, VersionReviewPersistence persistence, ProjectService projects, WardenClientService warden) {
        this.mongo = mongo; this.persistence = persistence; this.projects = projects; this.warden = warden;
    }

    public enum Disposition { ACCEPT, REQUIRE_REVIEW, REVOKE }
    public record Request(Integer issueIndex, Disposition disposition, String rationale) {}
    public record Finding(String identity, String path, String type, String description, int lineStart, int lineEnd) {}
    public record Event(@Id String id, String projectId, String versionId, String previousId, int sequence,
            String actorId, long createdAt, long expiresAt, Disposition disposition, String rationale,
            String scope, String artifactSha256, String contentSha256, String policyVersion,
            String contextSha256, Finding finding, String revokedDecisionId, String supersedesDecisionId) {}
    public record DecisionAssessment(String state, String explanation) {}
    public record History(List<Event> events, Integer nextOffset, Map<String, DecisionAssessment> assessments, long assessedAt) {}

    public Event record(String projectId, String versionId, String token, String actorId, Request request) {
        if (request == null || request.disposition() == null || request.disposition() == Disposition.REVOKE)
            throw invalid("Choose an acceptance or a requirement for further review");
        validateText(actorId, request.rationale());
        var snapshot = persistence.capture(projectId, versionId, token);
        var version = mongo.getConverter().read(ProjectVersion.class, snapshot.version());
        var scan = version.getScanResult();
        if (scan == null || scan.getIssues() == null || request.issueIndex() == null || request.issueIndex() < 0 || request.issueIndex() >= scan.getIssues().size())
            throw invalid("Select a finding from the inspected scan");
        var issue = scan.getIssues().get(request.issueIndex());
        var evidence = scan.getSecurityEvidence();
        String context = ArtifactReviewContext.fingerprint(version);
        String identity = IssueEvidenceIdentity.from(scan).identify(issue);
        if (identity == null || evidence == null || !Objects.equals(version.getHash(), evidence.artifactSha256())
                || context == null || !context.equals(scan.getReviewedContextSha256()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete artifact and context evidence is required to record this decision");
        if (issue.getDescription() != null && issue.getDescription().length() > 16000)
            throw invalid("The finding exceeds the supported decision record size");
        var previous = head(projectId, versionId, version.getFindingReviewHead());
        String supersedes = chain(projectId, versionId, previous).stream()
                .filter(event -> event.disposition() != Disposition.REVOKE && event.finding() != null
                        && identity.equals(event.finding().identity())
                        && evidence.contentSha256().equals(event.contentSha256()) && context.equals(event.contextSha256()))
                .map(Event::id).findFirst().orElse(null);
        long now = Instant.now().toEpochMilli();
        var event = new Event(UUID.randomUUID().toString(), projectId, versionId, version.getFindingReviewHead(),
                nextSequence(previous), actorId, now, request.disposition() == Disposition.ACCEPT ? now + 30L * 86_400_000 : 0,
                request.disposition(), request.rationale().strip(),
                "WHOLE_ARTIFACT", evidence.artifactSha256(), evidence.contentSha256(), evidence.policyVersion(), context,
                new Finding(identity, issue.getFilePath(), issue.getType(), issue.getDescription(), issue.getLineStart(), issue.getLineEnd()), null, supersedes);
        return persist(snapshot, event);
    }

    public Event revoke(String projectId, String versionId, String token, String actorId, String decisionId, String rationale) {
        validateText(actorId, rationale);
        var snapshot = persistence.capture(projectId, versionId, token);
        var version = mongo.getConverter().read(ProjectVersion.class, snapshot.version());
        var previous = head(projectId, versionId, version.getFindingReviewHead());
        Event target = null;
        for (Event event : chain(projectId, versionId, previous)) {
            if (Objects.equals(event.revokedDecisionId(), decisionId)) throw invalid("This decision was already revoked");
            if (event.id().equals(decisionId)) { target = event; break; }
        }
        if (target == null || target.disposition() == Disposition.REVOKE) throw invalid("Select an active history decision to revoke");
        var event = new Event(UUID.randomUUID().toString(), projectId, versionId, version.getFindingReviewHead(),
                nextSequence(previous), actorId, Instant.now().toEpochMilli(), target.expiresAt(), Disposition.REVOKE,
                rationale.strip(), target.scope(), target.artifactSha256(), target.contentSha256(), target.policyVersion(),
                target.contextSha256(), target.finding(), target.id(), null);
        return persist(snapshot, event);
    }

    public History history(String projectId, String versionId, String token, int offset) {
        if (offset < 0 || offset >= MAX_EVENTS) throw invalid("Invalid history offset");
        var snapshot = persistence.capture(projectId, versionId, token);
        var version = mongo.getConverter().read(ProjectVersion.class, snapshot.version());
        var events = chain(projectId, versionId, head(projectId, versionId, version.getFindingReviewHead()));
        int end = Math.min(events.size(), offset + 50);
        var page = List.copyOf(events.subList(Math.min(offset, events.size()), end));
        String policy = ArtifactClearancePolicy.complete(version.getScanResult()) && !events.isEmpty()
                ? warden.currentPolicyVersion() : null;
        long assessedAt = Instant.now().toEpochMilli();
        var validity = new FindingDecisionValidity().assess(projectId, version, events, policy, assessedAt);
        var assessments = new LinkedHashMap<String, DecisionAssessment>();
        for (var event : events) {
            var assessment = validity.get(event.id());
            assessments.put(event.id(), new DecisionAssessment(assessment.state().name(), assessment.explanation()));
        }
        // Policy lookup and history reads must not conceal a concurrent change to this version.
        persistence.capture(projectId, versionId, token);
        return new History(page, end < events.size() ? end : null, Map.copyOf(assessments), assessedAt);
    }

    private Event persist(VersionReviewPersistence.Snapshot snapshot, Event event) {
        // Only a successfully linked event is active; interrupted or losing inserts are unreachable history.
        mongo.insert(event, COLLECTION);
        if (!persistence.appendFindingReview(snapshot, event.id())) throw VersionReviewPersistence.conflict();
        var project = projects.getRawProjectById(event.projectId());
        if (project != null) projects.evictProjectCache(project);
        return event;
    }
    private Event head(String projectId, String versionId, String id) {
        if (id == null) return null;
        var event = mongo.findById(id, Event.class, COLLECTION);
        if (event == null || !projectId.equals(event.projectId()) || !versionId.equals(event.versionId())
                || event.sequence() <= 0 || event.sequence() > MAX_EVENTS) throw unavailable();
        return event;
    }
    private List<Event> chain(String projectId, String versionId, Event head) {
        var result = new ArrayList<Event>();
        int expected = head == null ? 0 : head.sequence();
        for (var event = head; event != null; event = head(projectId, versionId, event.previousId())) {
            if (event.sequence() != expected-- || result.size() >= MAX_EVENTS) throw unavailable();
            result.add(event);
        }
        if (expected != 0) throw unavailable();
        return result;
    }
    private int nextSequence(Event previous) {
        if (previous != null && previous.sequence() >= MAX_EVENTS)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This version reached the decision history limit");
        return previous == null ? 1 : previous.sequence() + 1;
    }
    private static void validateText(String actorId, String rationale) {
        if (actorId == null || actorId.isBlank() || actorId.length() > 256) throw invalid("A reviewer identity is required");
        if (rationale == null || rationale.strip().length() < 10 || rationale.length() > 4000)
            throw invalid("Explain the decision in 10 to 4000 characters");
    }
    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Decision history is incomplete or inconsistent");
    }
}
