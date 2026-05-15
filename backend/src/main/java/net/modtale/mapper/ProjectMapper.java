package net.modtale.mapper;

import net.modtale.model.dto.ProjectDTO;
import net.modtale.model.dto.ProjectVersionDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class ProjectMapper {

    public static ProjectDTO toDTO(Project project, boolean isSummary) {
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
            dto.setComments(project.getComments() != null ? project.getComments() : new ArrayList<>());

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
}