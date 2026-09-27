package net.modtale.service.admin.review;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminAuthorStatsDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewQueryService {

    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ProjectReviewQueueService projectReviewQueueService;
    private final MongoTemplate mongoTemplate;

    public ProjectReviewQueryService(
            UserRepository userRepository,
            ProjectService projectService,
            ProjectReviewQueueService projectReviewQueueService,
            MongoTemplate mongoTemplate
    ) {
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.projectReviewQueueService = projectReviewQueueService;
        this.mongoTemplate = mongoTemplate;
    }

    public List<AdminVerificationQueueItemDTO> getVerificationQueue() {
        return projectReviewQueueService.getVerificationQueue().stream()
                .map(ProjectMapper::toVerificationQueueItemDTO)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(ProjectReviewQueryService::queuePriority).reversed()
                        .thenComparing(item -> item.updatedAt() == null ? "" : item.updatedAt()))
                .toList();
    }

    private static int queuePriority(AdminVerificationQueueItemDTO item) {
        if (item.pendingVersion() == null || item.pendingVersion().scan() == null) return 2_000;
        var scan = item.pendingVersion().scan();
        String verdict = scan.verdict() == null ? "" : scan.verdict().toUpperCase(Locale.ROOT);
        int riskScore = scan.riskScore();

        if ("BLOCK".equals(verdict) || scan.status() == ScanStatus.INFECTED) {
            return 8_000 + riskScore + scan.newIssueCount() * 10 + scan.escalatedIssueCount() * 15;
        }
        if (scan.newIssueCount() > 0 || scan.escalatedIssueCount() > 0) {
            return 6_000 + riskScore + scan.newIssueCount() * 6 + scan.escalatedIssueCount() * 10;
        }
        if ("REVIEW".equals(verdict)
                || scan.status() == ScanStatus.SUSPICIOUS
                || scan.status() == ScanStatus.FLAGGED) {
            return 4_000 + riskScore;
        }
        return 2_000 + riskScore;
    }

    public AdminProjectReviewDTO getProjectReviewDetails(String id) {
        Project project = requireProject(id);
        User author = userRepository.findById(project.getAuthorId()).orElse(null);

        AdminAuthorStatsDTO authorStats = new AdminAuthorStatsDTO(
                author != null ? author.getCreatedAt() : "Unknown",
                author != null ? author.getTier().name() : "Unknown",
                author != null && author.getAvatarUrl() != null ? author.getAvatarUrl() : "",
                author != null
                        ? mongoTemplate.count(new Query(Criteria.where("authorId").is(author.getId())
                                .and("status").is(ProjectStatus.PUBLISHED).and("deletedAt").is(null)), Project.class)
                        : 0
        );

        return new AdminProjectReviewDTO(ProjectMapper.toAdminDTO(project), authorStats);
    }

    private Project requireProject(String id) {
        Document fields = new Document();
        for (String field : List.of("slug", "title", "about", "description", "authorId", "author",
                "imageUrl", "bannerUrl", "classification", "tags", "downloadCount", "favoriteCount",
                "repositoryUrl", "updatedAt", "createdAt", "license", "customLicenseOpenSource",
                "links", "childProjectIds", "allowModpacks", "allowComments", "hmWikiEnabled",
                "hmWikiSlug", "galleryCarouselEnabled", "status", "expiresAt", "deletedAt",
                "approvedBy", "galleryImages", "galleryImageCaptions", "projectRoles",
                "teamMembers", "teamInvites")) {
            fields.put(field, 1);
        }
        fields.put("versions", Document.parse("""
                { "$let": {
                    "vars": { "v": { "$ifNull": [
                        { "$first": { "$filter": {
                            "input": "$versions", "as": "candidate",
                            "cond": { "$eq": ["$$candidate.reviewStatus", "PENDING"] }
                        } } },
                        { "$first": "$versions" }
                    ] } },
                    "in": { "$cond": [ { "$ne": ["$$v", null] }, ["$$v"], [] ] }
                } }
                """));
        Object mongoId = ObjectId.isValid(id) ? new ObjectId(id) : id;
        Aggregation aggregation = Aggregation.newAggregation(
                context -> new Document("$match", new Document("_id", mongoId)),
                context -> new Document("$project", fields)
        );
        Project project = mongoTemplate.aggregate(aggregation, "projects", Project.class)
                .getUniqueMappedResult();
        if (project == null && mongoId instanceof ObjectId) {
            project = projectService.getRawProjectById(id);
        }
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }
}
