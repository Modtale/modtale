package net.modtale.controller.admin;

import net.modtale.model.admin.AdminLog;
import net.modtale.repository.admin.AdminLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditControllerTest {

    private AuditController controller;
    private AdminLogRepository adminLogRepository;

    @BeforeEach
    void setUp() {
        adminLogRepository = mock(AdminLogRepository.class);
        controller = new AuditController(adminLogRepository);
    }

    @Test
    void getAdminLogsUsesDescendingTimestampPagingAndMapsDtos() {
        AdminLog log = new AdminLog();
        log.setId("log-1");
        log.setAdminUsername("ada");
        log.setAction("PUBLISH");
        log.setTargetId("project-1");
        log.setTargetType("PROJECT");
        log.setDetails("Published project");
        log.setTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4));

        Page<AdminLog> page = new PageImpl<>(List.of(log));
        when(adminLogRepository.findWithFilters(eq("ada"), eq("PUBLISH"), eq("PROJECT"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        var response = controller.getAdminLogs("ada", "PUBLISH", "PROJECT", 2, 25);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("log-1", response.getBody().getContent().getFirst().id());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminLogRepository).findWithFilters(eq("ada"), eq("PUBLISH"), eq("PROJECT"), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(25, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "timestamp"), pageableCaptor.getValue().getSort());
    }
}
