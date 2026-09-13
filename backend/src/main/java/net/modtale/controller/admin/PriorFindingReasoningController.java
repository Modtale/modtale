package net.modtale.controller.admin;

import net.modtale.service.security.issue.PriorFindingReasoningService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/projects/{projectId}/versions/{versionId}/prior-finding-reasoning")
public class PriorFindingReasoningController {
    private final PriorFindingReasoningService reasoning;
    public PriorFindingReasoningController(PriorFindingReasoningService reasoning) { this.reasoning=reasoning; }
    @GetMapping
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
    public ResponseEntity<PriorFindingReasoningService.Reasoning> read(@PathVariable String projectId,@PathVariable String versionId,
            @RequestParam String sourceVersionId,@RequestParam int issueIndex,@RequestHeader("If-Match") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reasoning.read(projectId,versionId,sourceVersionId,issueIndex,token));
    }
}
