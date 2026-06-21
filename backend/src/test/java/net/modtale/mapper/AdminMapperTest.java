package net.modtale.mapper;

import java.time.LocalDateTime;
import net.modtale.model.admin.AdminLog;
import net.modtale.model.admin.BannedEmail;
import net.modtale.model.dto.admin.AdminLogDTO;
import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.admin.ReportDTO;
import net.modtale.model.user.Report;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminMapperTest {

    @Test
    void toLogDTOMapsAdminAuditFields() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 12, 0);
        AdminLog log = new AdminLog();
        log.setId("log-1");
        log.setAdminUsername("mod");
        log.setAction("DELETE");
        log.setTargetId("project-1");
        log.setTargetType("PROJECT");
        log.setDetails("Removed spam");
        log.setTimestamp(timestamp);

        AdminLogDTO dto = AdminMapper.toLogDTO(log);

        assertEquals("log-1", dto.id());
        assertEquals("mod", dto.adminUsername());
        assertEquals("DELETE", dto.action());
        assertEquals(timestamp, dto.timestamp());
        assertNull(AdminMapper.toLogDTO(null));
    }

    @Test
    void toBannedEmailDTOMapsModerationMetadata() {
        LocalDateTime bannedAt = LocalDateTime.of(2026, 1, 2, 9, 30);
        BannedEmail bannedEmail = new BannedEmail();
        bannedEmail.setId("ban-1");
        bannedEmail.setEmail("spam@example.com");
        bannedEmail.setReason("abuse");
        bannedEmail.setBannedBy("mod");
        bannedEmail.setBannedAt(bannedAt);

        BannedEmailDTO dto = AdminMapper.toBannedEmailDTO(bannedEmail);

        assertEquals("ban-1", dto.id());
        assertEquals("spam@example.com", dto.email());
        assertEquals("abuse", dto.reason());
        assertEquals(bannedAt, dto.bannedAt());
        assertNull(AdminMapper.toBannedEmailDTO(null));
    }

    @Test
    void toReportDTOMapsReportLifecycleFields() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 3, 8, 15);
        Report report = new Report();
        report.setId("report-1");
        report.setReporterId("user-1");
        report.setReporterUsername("ada");
        report.setTargetId("project-1");
        report.setTargetType(Report.TargetType.PROJECT);
        report.setTargetSummary("Sky Tools");
        report.setReason("Malware");
        report.setDescription("Unexpected behavior");
        report.setStatus(Report.ReportStatus.OPEN);
        report.setCreatedAt(createdAt);
        report.setResolvedBy("mod");
        report.setResolutionNote("Investigating");

        ReportDTO dto = AdminMapper.toReportDTO(report);

        assertEquals("report-1", dto.id());
        assertEquals("ada", dto.reporterUsername());
        assertEquals(Report.TargetType.PROJECT, dto.targetType());
        assertEquals(Report.ReportStatus.OPEN, dto.status());
        assertEquals(createdAt, dto.createdAt());
        assertNull(AdminMapper.toReportDTO(null));
    }
}
