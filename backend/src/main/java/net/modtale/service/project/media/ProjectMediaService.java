package net.modtale.service.project.media;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectMediaOperationException;
import net.modtale.exception.StorageUploadException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.lifecycle.ProjectDeletionService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.validation.FileValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectMediaService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final MediaUploadService mediaUploadService;
    private final ProjectDeletionService projectDeletionService;
    private final FileValidationService fileValidationService;
    private final int maxGalleryImages;
    private static final int MAX_GALLERY_CAPTION_LENGTH = 240;
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    public ProjectMediaService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            MediaUploadService mediaUploadService,
            ProjectDeletionService projectDeletionService,
            FileValidationService fileValidationService,
            AppLimitProperties limitProperties
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.mediaUploadService = mediaUploadService;
        this.projectDeletionService = projectDeletionService;
        this.fileValidationService = fileValidationService;
        this.maxGalleryImages = limitProperties.maxGalleryImagesPerProject();
    }

    public void updateProjectImage(String id, MultipartFile file, User user, boolean isBanner) {
        String permission = isBanner ? "PROJECT_EDIT_BANNER" : "PROJECT_EDIT_ICON";
        Project project = projectAccessService.requireProjectPermission(id, user, permission,
                "You do not have permission to update this project's image.");
        projectMutationGuard.ensureEditable(project);

        try {
            String currentUrl = isBanner ? project.getBannerUrl() : project.getImageUrl();
            String publicUrl = mediaUploadService.uploadPublicUrl(
                    file,
                    "images",
                    isBanner ? fileValidationService::validateBanner : fileValidationService::validateIcon,
                    () -> {
                        if (currentUrl != null && !currentUrl.contains("default.png") && !currentUrl.contains("placeholder") && !currentUrl.contains("favicon")) {
                            projectDeletionService.deleteStoredFile(currentUrl);
                        }
                    }
            );

            if (isBanner) {
                project.setBannerUrl(publicUrl);
            } else {
                project.setImageUrl(publicUrl);
            }

            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } catch (StorageUploadException ex) {
            throw new ProjectMediaOperationException(ex.getMessage(), ex);
        }
    }

    public Project addGalleryImage(String id, MultipartFile file, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_ADD",
                "You do not have permission to upload gallery images for this project.");
        projectMutationGuard.ensureEditable(project);
        ensureGalleryCapacity(project);

        try {
            galleryItems(project).add(mediaUploadService.uploadPublicUrl(file, "gallery", fileValidationService::validateGalleryImage));
            return saveAndEvict(project);
        } catch (StorageUploadException ex) {
            throw new ProjectMediaOperationException(ex.getMessage(), ex);
        }
    }

    public Project addGalleryVideo(String id, String videoUrl, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_ADD",
                "You do not have permission to add gallery videos for this project.");
        projectMutationGuard.ensureEditable(project);
        ensureGalleryCapacity(project);

        String normalizedVideoUrl = normalizeYouTubeUrl(videoUrl);
        List<String> galleryItems = galleryItems(project);
        if (galleryItems.contains(normalizedVideoUrl)) {
            throw new InvalidProjectRequestException("That YouTube video is already in this project gallery.");
        }

        galleryItems.add(normalizedVideoUrl);
        return saveAndEvict(project);
    }

    public Project removeGalleryImage(String id, String imageUrl, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_REMOVE",
                "You do not have permission to remove gallery images from this project.");
        projectMutationGuard.ensureEditable(project);
        galleryItems(project).remove(imageUrl);
        if (project.getGalleryImageCaptions() != null && project.getGalleryImageCaptions().containsKey(imageUrl)) {
            Map<String, String> captions = new HashMap<>(project.getGalleryImageCaptions());
            captions.remove(imageUrl);
            project.setGalleryImageCaptions(captions);
        }
        if (!isYouTubeUrl(imageUrl)) {
            projectDeletionService.deleteStoredFile(imageUrl);
        }
        return saveAndEvict(project);
    }

    public Project updateGalleryImageCaption(String id, String imageUrl, String caption, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_ADD",
                "You do not have permission to edit gallery image captions for this project.");
        projectMutationGuard.ensureEditable(project);

        if (project.getGalleryImages() == null || !project.getGalleryImages().contains(imageUrl)) {
            throw new InvalidProjectRequestException("That gallery image does not exist on this project.");
        }

        String normalizedCaption = caption == null ? "" : caption.trim();
        if (normalizedCaption.length() > MAX_GALLERY_CAPTION_LENGTH) {
            throw new InvalidProjectRequestException("Gallery image captions must be " + MAX_GALLERY_CAPTION_LENGTH + " characters or fewer.");
        }

        Map<String, String> captions = new HashMap<>(
                project.getGalleryImageCaptions() == null ? Map.of() : project.getGalleryImageCaptions()
        );

        if (normalizedCaption.isBlank()) {
            captions.remove(imageUrl);
        } else {
            captions.put(imageUrl, normalizedCaption);
        }
        project.setGalleryImageCaptions(captions);

        Project saved = projectRepository.save(project);
        Project cacheTarget = saved != null ? saved : project;
        projectService.evictProjectCache(cacheTarget);
        return cacheTarget;
    }

    private Project saveAndEvict(Project project) {
        Project saved = projectRepository.save(project);
        Project cacheTarget = saved != null ? saved : project;
        projectService.evictProjectCache(cacheTarget);
        return cacheTarget;
    }

    private void ensureGalleryCapacity(Project project) {
        if (galleryItems(project).size() >= maxGalleryImages) {
            throw new InvalidProjectRequestException("This project has already reached the gallery limit of " + maxGalleryImages + " items.");
        }
    }

    private List<String> galleryItems(Project project) {
        if (project.getGalleryImages() == null) {
            project.setGalleryImages(new ArrayList<>());
        }
        return project.getGalleryImages();
    }

    private String normalizeYouTubeUrl(String videoUrl) {
        String videoId = extractYouTubeVideoId(videoUrl)
                .orElseThrow(() -> new InvalidProjectRequestException("Gallery videos must be valid YouTube video URLs."));
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private boolean isYouTubeUrl(String videoUrl) {
        return extractYouTubeVideoId(videoUrl).isPresent();
    }

    private Optional<String> extractYouTubeVideoId(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(videoUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }

            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }

            String normalizedHost = host.toLowerCase();
            if (normalizedHost.startsWith("www.")) normalizedHost = normalizedHost.substring(4);
            if (normalizedHost.startsWith("m.")) normalizedHost = normalizedHost.substring(2);

            String candidate = null;
            if (normalizedHost.equals("youtu.be")) {
                candidate = firstPathSegment(uri.getPath());
            } else if (normalizedHost.equals("youtube.com") || normalizedHost.equals("youtube-nocookie.com")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.equals("/watch")) {
                    candidate = queryParam(uri.getRawQuery(), "v");
                } else if (path.startsWith("/embed/") || path.startsWith("/shorts/") || path.startsWith("/live/")) {
                    candidate = firstPathSegment(path.substring(path.indexOf('/', 1) + 1));
                }
            }

            if (candidate == null) {
                return Optional.empty();
            }

            int queryIndex = candidate.indexOf('?');
            if (queryIndex >= 0) candidate = candidate.substring(0, queryIndex);
            int slashIndex = candidate.indexOf('/');
            if (slashIndex >= 0) candidate = candidate.substring(0, slashIndex);

            return YOUTUBE_VIDEO_ID_PATTERN.matcher(candidate).matches()
                    ? Optional.of(candidate)
                    : Optional.empty();
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private String firstPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        int slashIndex = normalizedPath.indexOf('/');
        return slashIndex >= 0 ? normalizedPath.substring(0, slashIndex) : normalizedPath;
    }

    private String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            int equalsIndex = part.indexOf('=');
            String key = equalsIndex >= 0 ? part.substring(0, equalsIndex) : part;
            if (key.equals(name)) {
                return equalsIndex >= 0 ? part.substring(equalsIndex + 1) : "";
            }
        }
        return null;
    }
}
