package net.modtale.controller.admin;

import net.modtale.model.user.User;
import net.modtale.repository.admin.AdminLogRepository;
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

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    private boolean isSuperAdmin(User user) {
        return user != null && SUPER_ADMIN_ID.equals(user.getId());
    }

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
        if (!isSuperAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return ResponseEntity.ok(adminLogRepository.findWithFilters(query, action, targetType, pageable));
    }
}