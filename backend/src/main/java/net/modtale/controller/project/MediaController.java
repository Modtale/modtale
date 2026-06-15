package net.modtale.controller.project;

import jakarta.validation.Valid;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.request.project.RemoveGalleryImageRequest;
import net.modtale.model.dto.request.project.UpdateGalleryImageCaptionRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.project.media.ProjectMediaService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
public class MediaController {

    private final ProjectMediaService projectMediaService;
    private final AccountService accountService;

    public MediaController(ProjectMediaService projectMediaService, AccountService accountService) {
        this.projectMediaService = projectMediaService;
        this.accountService = accountService;
    }

    @PutMapping("/{id}/icon")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_ICON', authentication)")
    public ResponseEntity<Void> updateIcon(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating a project icon");
        projectMediaService.updateProjectImage(id, file, user, false);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/banner")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_BANNER', authentication)")
    public ResponseEntity<Void> updateBanner(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating a project banner");
        projectMediaService.updateProjectImage(id, file, user, true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/gallery")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_GALLERY_ADD', authentication)")
    public ResponseEntity<Void> addGalleryImage(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "uploading a gallery image");
        projectMediaService.addGalleryImage(id, file, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/gallery")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_GALLERY_REMOVE', authentication)")
    public ResponseEntity<Void> removeGalleryImage(
            @PathVariable String id,
            @Valid @RequestBody RemoveGalleryImageRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "removing a gallery image");
        projectMediaService.removeGalleryImage(id, requestPayload.getImageUrl(), user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/gallery/caption")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_GALLERY_ADD', authentication)")
    public ResponseEntity<ProjectDTO> updateGalleryImageCaption(
            @PathVariable String id,
            @Valid @RequestBody UpdateGalleryImageCaptionRequest requestPayload,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating a gallery image caption");
        Project project = projectMediaService.updateGalleryImageCaption(id, requestPayload.getImageUrl(), requestPayload.getCaption(), user);
        return ResponseEntity.ok(ProjectMapper.toDTO(project, false, user.getId()));
    }
}
