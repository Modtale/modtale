package net.modtale.mapper;

import net.modtale.model.admin.AdminLog;
import net.modtale.model.admin.BannedEmail;
import net.modtale.model.dto.admin.AdminLogDTO;
import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.admin.ReportDTO;
import net.modtale.model.user.Report;

public class AdminMapper {
    public static AdminLogDTO toLogDTO(AdminLog log) {
        if (log == null) return null;
        return new AdminLogDTO(
                log.getId(),
                log.getAdminUsername(),
                log.getAction(),
                log.getTargetId(),
                log.getTargetType(),
                log.getDetails(),
                log.getTimestamp()
        );
    }

    public static BannedEmailDTO toBannedEmailDTO(BannedEmail bannedEmail) {
        if (bannedEmail == null) return null;
        return new BannedEmailDTO(
                bannedEmail.getId(),
                bannedEmail.getEmail(),
                bannedEmail.getReason(),
                bannedEmail.getBannedBy(),
                bannedEmail.getBannedAt()
        );
    }

    public static ReportDTO toReportDTO(Report report) {
        if (report == null) return null;
        return new ReportDTO(
                report.getId(),
                report.getReporterId(),
                report.getReporterUsername(),
                report.getTargetId(),
                report.getTargetType(),
                report.getTargetSummary(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedBy(),
                report.getResolutionNote()
        );
    }
}
