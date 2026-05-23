package net.modtale.mapper;

import net.modtale.model.dto.admin.AdminProjectDTO;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectCommentDTO;
import net.modtale.model.dto.project.ProjectCommentReplyDTO;
import net.modtale.model.dto.project.ProjectDependencyDTO;
import net.modtale.model.dto.project.ProjectMetaDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.project.ProjectVersionDTO;
import net.modtale.model.dto.project.ProjectVersionSummaryDTO;
import net.modtale.model.project.Comment;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectMapper {

    public static ProjectSummaryDTO toSummaryDTO(Project project) {
        return toSummaryDTO(project, false);
    }

    public static ProjectSummaryDTO toSummaryDTO(Project project, boolean includeManagementFields) {
        if (project == null) return null;
        return new ProjectSummaryDTO(
                project.getId(),
                project.getSlug(),
                project.getTitle(),
                project.getDescription(),
                project.getAuthorId(),
                project.getAuthor(),
                project.getImageUrl(),
                project.getBannerUrl(),
                project.getClassification(),
                project.getDownloadCount(),
                project.getFavoriteCount(),
                project.getUpdatedAt(),
                project.getChildProjectIds(),
                includeManagementFields ? project.getStatus() : null,
                includeManagementFields ? project.isCanEdit() : null,
                includeManagementFields ? project.isOwner() : null,
                includeManagementFields ? toVersionSummaryDTOs(project.getVersions(), true) : null
        );
    }

    public static ProjectMetaDTO toMetaDTO(Project project) {
        if (project == null) return null;
        return new ProjectMetaDTO(
                project.getTitle(),
                project.getDescription() != null ? project.getDescription() : "",
                project.getImageUrl() != null ? project.getImageUrl() : "",
                project.getAuthor(),
                project.getClassification(),
                project.getDownloadCount(),
                project.getRepositoryUrl() != null ? project.getRepositoryUrl() : "",
                project.getSlug() != null ? project.getSlug() : project.getId()
        );
    }

    public static AdminProjectDTO toAdminDTO(Project project) {
        if (project == null) return null;
        return new AdminProjectDTO(
                project.getId(),
                project.getSlug(),
                project.getTitle(),
                project.getAbout(),
                project.getDescription(),
                project.getAuthorId(),
                project.getAuthor(),
                project.getImageUrl(),
                project.getBannerUrl(),
                project.getClassification(),
                project.getTags(),
                project.getDownloadCount(),
                project.getFavoriteCount(),
                project.getRepositoryUrl(),
                project.getUpdatedAt(),
                project.getCreatedAt(),
                project.getLicense(),
                project.getLinks(),
                project.getChildProjectIds(),
                project.isAllowModpacks(),
                project.isAllowComments(),
                project.isHmWikiEnabled(),
                project.getHmWikiSlug(),
                project.getStatus(),
                project.getExpiresAt(),
                project.getDeletedAt(),
                project.getApprovedBy(),
                project.getContributors(),
                project.getGalleryImages(),
                project.getProjectRoles(),
                project.getTeamMembers(),
                project.getTeamInvites(),
                toVersionSummaryDTOs(project.getVersions(), true)
        );
    }

    public static ProjectDTO toDTO(Project project, boolean isSummary) {
        return toDTO(project, isSummary, null);
    }

    public static ProjectDTO toDTO(Project project, boolean isSummary, String currentUserId) {
        if (project == null) return null;
        ProjectDTO dto = new ProjectDTO();

        dto.setId(project.getId());
        dto.setSlug(project.getSlug());
        dto.setTitle(project.getTitle());
        dto.setDescription(project.getDescription());
        dto.setAuthorId(project.getAuthorId());
        dto.setAuthor(project.getAuthor());
        dto.setImageUrl(project.getImageUrl());
        dto.setBannerUrl(project.getBannerUrl());
        dto.setClassification(project.getClassification());
        dto.setCategories(project.getCategories());
        dto.setTags(project.getTags());
        dto.setDownloadCount(project.getDownloadCount());
        dto.setFavoriteCount(project.getFavoriteCount());
        dto.setTrendScore(project.getTrendScore());
        dto.setRelevanceScore(project.getRelevanceScore());
        dto.setPopularScore(project.getPopularScore());
        dto.setRepositoryUrl(project.getRepositoryUrl());
        dto.setUpdatedAt(project.getUpdatedAt());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setLicense(project.getLicense());
        dto.setLastTrendingNotification(project.getLastTrendingNotification());
        dto.setLinks(project.getLinks());
        dto.setTypes(project.getTypes());
        dto.setAllowModpacks(project.isAllowModpacks());
        dto.setAllowComments(project.isAllowComments());
        dto.setHmWikiEnabled(project.isHmWikiEnabled());
        dto.setHmWikiSlug(project.getHmWikiSlug());
        dto.setStatus(project.getStatus());
        dto.setExpiresAt(project.getExpiresAt());
        dto.setCanEdit(project.isCanEdit());
        dto.setIsOwner(project.isOwner());

        if (!isSummary) {
            dto.setAbout(project.getAbout());
            dto.setChildProjectIds(project.getChildProjectIds());
            dto.setModIds(project.getModIds());

            dto.setProjectRoles(project.getProjectRoles());
            dto.setTeamMembers(project.getTeamMembers());
            dto.setTeamInvites(project.getTeamInvites());

            dto.setContributors(project.getContributors());
            dto.setGalleryImages(project.getGalleryImages());
            dto.setComments(project.getComments() != null
                    ? project.getComments().stream()
                            .map(comment -> toCommentDTO(comment, currentUserId))
                            .collect(Collectors.toList())
                    : new ArrayList<>());

            if (project.getVersions() != null) {
                dto.setVersions(project.getVersions().stream()
                        .map(ProjectMapper::toVersionDTO)
                        .collect(Collectors.toList()));
            } else {
                dto.setVersions(new ArrayList<>());
            }
        }

        return dto;
    }

    public static ProjectCommentDTO toCommentDTO(Comment comment, String currentUserId) {
        if (comment == null) return null;
        return new ProjectCommentDTO(
                comment.getId(),
                comment.getUserId(),
                comment.getContent(),
                comment.getDate(),
                comment.getUpdatedAt(),
                comment.getUpvotes() != null ? comment.getUpvotes().size() : 0,
                comment.getDownvotes() != null ? comment.getDownvotes().size() : 0,
                resolveVote(comment.getUpvotes(), comment.getDownvotes(), currentUserId),
                toCommentReplyDTO(comment.getDeveloperReply(), currentUserId)
        );
    }

    public static ProjectCommentReplyDTO toCommentReplyDTO(Comment.Reply reply, String currentUserId) {
        if (reply == null) return null;
        return new ProjectCommentReplyDTO(
                reply.getUserId(),
                reply.getContent(),
                reply.getDate(),
                reply.getUpvotes() != null ? reply.getUpvotes().size() : 0,
                reply.getDownvotes() != null ? reply.getDownvotes().size() : 0,
                resolveVote(reply.getUpvotes(), reply.getDownvotes(), currentUserId)
        );
    }

    private static String resolveVote(java.util.Set<String> upvotes, java.util.Set<String> downvotes, String currentUserId) {
        if (currentUserId == null) return null;
        if (upvotes != null && upvotes.contains(currentUserId)) return "up";
        if (downvotes != null && downvotes.contains(currentUserId)) return "down";
        return null;
    }

    public static ProjectVersionDTO toVersionDTO(ProjectVersion version) {
        if (version == null) return null;
        ProjectVersionDTO dto = new ProjectVersionDTO();
        dto.setId(version.getId());
        dto.setVersionNumber(version.getVersionNumber());
        dto.setGameVersions(version.getGameVersions());
        dto.setFileUrl(version.getFileUrl());
        dto.setDownloadCount(version.getDownloadCount());
        dto.setReleaseDate(version.getReleaseDate());
        dto.setChangelog(version.getChangelog());
        dto.setDependencies(version.getDependencies());
        dto.setChannel(version.getChannel());
        return dto;
    }

    public static ProjectVersionSummaryDTO toVersionSummaryDTO(ProjectVersion version, boolean includeReviewData) {
        if (version == null) return null;
        return new ProjectVersionSummaryDTO(
                version.getId(),
                version.getVersionNumber(),
                version.getGameVersions(),
                version.getDownloadCount(),
                version.getReleaseDate(),
                version.getChannel(),
                includeReviewData ? version.getReviewStatus() : null,
                includeReviewData ? version.getRejectionReason() : null,
                includeReviewData ? version.getScanResult() : null
        );
    }

    public static List<ProjectVersionSummaryDTO> toVersionSummaryDTOs(List<ProjectVersion> versions, boolean includeReviewData) {
        if (versions == null) return new ArrayList<>();
        return versions.stream()
                .map(v -> toVersionSummaryDTO(v, includeReviewData))
                .collect(Collectors.toList());
    }

    public static ProjectDependencyDTO toDependencyDTO(ProjectDependency dependency) {
        if (dependency == null) return null;
        return new ProjectDependencyDTO(
                dependency.getModId(),
                dependency.getModTitle(),
                dependency.getVersionNumber(),
                dependency.isOptional()
        );
    }
}
