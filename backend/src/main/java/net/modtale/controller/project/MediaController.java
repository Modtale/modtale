package net.modtale.controller.project;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.model.dto.request.project.RemoveGalleryImageRequest;
import net.modtale.model.user.User;
import net.modtale.service.project.MetadataService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
public class MediaController {

    @Autowired private MetadataService metadataService;
    @Autowired private AccountService accountService;

    @PutMapping("/{id}/icon")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_ICON', authentication)")
    public ResponseEntity<?> updateIcon(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating a project icon.");
        try { metadataService.updateProjectImage(id, file, user, false); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to update this project icon."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not update that project icon."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to update project icon."); }
    }

    @PutMapping("/{id}/banner")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_EDIT_BANNER', authentication)")
    public ResponseEntity<?> updateBanner(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before updating a project banner.");
        try { metadataService.updateProjectImage(id, file, user, true); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to update this project banner."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not update that project banner."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to update project banner."); }
    }

    @PostMapping("/{id}/gallery")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_GALLERY_ADD', authentication)")
    public ResponseEntity<?> addGalleryImage(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before uploading a gallery image.");
        try { metadataService.addGalleryImage(id, file, user); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to upload gallery images for this project."); }
        catch (IllegalArgumentException | IllegalStateException e) { return ErrorMessageUtils.badRequest(e, "We could not upload that gallery image."); }
        catch (Exception e) { return ErrorMessageUtils.internalServerError(e, "Failed to upload gallery image."); }
    }


    @DeleteMapping("/{id}/gallery")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_GALLERY_REMOVE', authentication)")
    public ResponseEntity<?> removeGalleryImage(
            @PathVariable String id,
            @RequestBody RemoveGalleryImageRequest requestPayload
    ) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before removing a gallery image.");

        String imageUrl = requestPayload.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            return ErrorMessageUtils.badRequest("An image URL is required before a gallery image can be removed.");
        }

        try {
            metadataService.removeGalleryImage(id, imageUrl, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ErrorMessageUtils.forbidden(e, "You do not have permission to remove gallery images from this project.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ErrorMessageUtils.badRequest(e, "We could not remove that gallery image.");
        } catch (Exception e) {
            return ErrorMessageUtils.internalServerError(e, "Failed to remove gallery image.");
        }
    }
}
