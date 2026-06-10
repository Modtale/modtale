package net.modtale.controller.admin;

import net.modtale.mapper.AdminMapper;
import net.modtale.model.dto.admin.AdminLogDTO;
import net.modtale.repository.admin.AdminLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AuditController {

    private final AdminLogRepository adminLogRepository;

    public AuditController(AdminLogRepository adminLogRepository) {
        this.adminLogRepository = adminLogRepository;
    }

    @GetMapping("/logs")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Page<AdminLogDTO>> getAdminLogs(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "") String action,
            @RequestParam(required = false, defaultValue = "") String targetType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(adminLogRepository.findWithFilters(query, action, targetType, pageable)
                .map(AdminMapper::toLogDTO));
    }
}
