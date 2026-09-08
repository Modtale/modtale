package net.modtale.model.dto.project;

import java.util.List;
import java.util.Map;
import net.modtale.service.project.version.CurseForgeApiClient;

public final class CurseForgeCatalogDTO {

    private CurseForgeCatalogDTO() {
    }

    public record Page(List<Project> content, int totalPages, long totalElements, int number, boolean last) {
        public static Page from(CurseForgeApiClient.CurseForgeSearchResult result) {
            int size = Math.max(1, result.pageSize());
            int page = result.index() / size;
            int pages = result.totalCount() == 0 ? 0 : (int) Math.ceil(result.totalCount() / (double) size);
            return new Page(result.projects().stream().map(Project::from).toList(), pages,
                    result.totalCount(), page, page + 1 >= pages);
        }
    }

    public record Project(
            String id,
            String slug,
            String title,
            String about,
            String description,
            String authorId,
            String author,
            String imageUrl,
            String bannerUrl,
            String classification,
            int downloadCount,
            int favoriteCount,
            String updatedAt,
            String license,
            String repositoryUrl,
            Map<String, String> links,
            List<String> tags,
            List<String> galleryImages,
            Map<String, String> galleryImageCaptions,
            Boolean allowComments,
            boolean hmWikiEnabled,
            String hmWikiSlug,
            List<Version> versions,
            String source,
            String websiteUrl,
            Boolean distributionAllowed
    ) {
        public static Project from(CurseForgeApiClient.CurseForgeProject project) {
            String providerId = "curseforge:" + project.id();
            Map<String, String> links = project.websiteUrl() == null
                    ? Map.of()
                    : Map.of("CurseForge", project.websiteUrl());
            return new Project(
                    providerId, providerId, project.title(), project.description(), project.summary(), null,
                    String.join(", ", project.authors()), project.iconUrl(), null, "MOD",
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0, project.downloadCount())), 0,
                    project.dateModified(), null, null, links, project.categories(), project.screenshots(), Map.of(),
                    false, false, null, project.files().stream().map(file -> Version.from(project, file)).toList(),
                    "CURSEFORGE", project.websiteUrl(), project.distributionAllowed()
            );
        }
    }

    public record Version(
            String id,
            String versionNumber,
            List<String> gameVersions,
            String fileUrl,
            int downloadCount,
            String releaseDate,
            String changelog,
            List<Object> dependencies,
            String channel,
            List<String> incompatibleProjectIds
    ) {
        static Version from(CurseForgeApiClient.CurseForgeProject project, CurseForgeApiClient.CurseForgeFile file) {
            return new Version(file.id(), file.versionNumber(), file.gameVersions(),
                    project.websiteUrl() + "/files/" + file.id(),
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0, file.downloadCount())), file.fileDate(), null,
                    List.of(), file.releaseType(), List.of());
        }
    }

    public record Download(
            String downloadUrl,
            int expiresIn,
            String fileName,
            Long fileSize,
            Map<String, String> hashes,
            String source
    ) {
        public static Download from(CurseForgeApiClient.CurseForgeDownload download) {
            return new Download(download.downloadUrl(), 0, download.fileName(), download.fileSize(),
                    download.hashes(), "CURSEFORGE");
        }
    }
}
