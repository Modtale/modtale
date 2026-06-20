package net.modtale.controller.worldlist;

import jakarta.validation.Valid;
import java.io.IOException;
import net.modtale.model.dto.request.worldlist.CreateWorldModListRequest;
import net.modtale.model.dto.worldlist.WorldModListDTO;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.worldlist.WorldModListService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorldModListController {

    private final WorldModListService service;
    private final AccountService accountService;

    public WorldModListController(WorldModListService service, AccountService accountService) {
        this.service = service;
        this.accountService = accountService;
    }

    @PostMapping("/lists")
    public ResponseEntity<WorldModListDTO> create(
            @Valid @RequestBody CreateWorldModListRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.create(
                request,
                accountService.requireCurrentUser(authentication, "sharing a world mod list")
        ));
    }

    @GetMapping("/lists/{id}")
    public ResponseEntity<WorldModListDTO> view(@PathVariable String id) {
        return ResponseEntity.ok(service.view(id));
    }

    @GetMapping("/lists/{id}/install")
    public ResponseEntity<WorldModListDTO> installMetadata(@PathVariable String id) {
        return ResponseEntity.ok(service.metadataForInstall(id));
    }

    @GetMapping("/lists/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException {
        WorldModListService.Download download = service.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(download.bytes()));
    }
}
