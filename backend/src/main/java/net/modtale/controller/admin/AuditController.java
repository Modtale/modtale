package net.modtale.controller.admin;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.mapper.AdminMapper;
import net.modtale.model.user.User;
import net.modtale.repository.admin.AdminLogRepository;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AuditController {

    @Autowired private AccountService accountService;
    @Autowired private AdminLogRepository adminLogRepository;
    @Autowired private AccessControlService accessControlService;

    private User getSafeUser() {
        try {
            return accountService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getAdminLogs(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "") String action,
            @RequestParam(required = false, defaultValue = "") String targetType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) return ErrorMessageUtils.forbidden("Only Super Admin can view audit logs.");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(adminLogRepository.findWithFilters(query, action, targetType, pageable)
                .map(AdminMapper::toLogDTO));
    }
}
