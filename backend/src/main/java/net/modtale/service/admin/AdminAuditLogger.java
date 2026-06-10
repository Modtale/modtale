package net.modtale.service.admin;

import net.modtale.model.admin.AdminLog;
import net.modtale.repository.admin.AdminLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditLogger {

    private final AdminLogRepository adminLogRepository;

    public AdminAuditLogger(AdminLogRepository adminLogRepository) {
        this.adminLogRepository = adminLogRepository;
    }

    public void logAction(String adminId, String action, String targetId, String targetType, String details) {
        adminLogRepository.save(new AdminLog(adminId, action, targetId, targetType, details));
    }
}
