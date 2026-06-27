package net.modtale.service.system;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.model.dto.response.system.StatusIncidentView;
import net.modtale.model.system.StatusIncident;
import net.modtale.model.system.StatusIncidentKind;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;
import net.modtale.repository.system.StatusIncidentRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatusIncidentServiceTest {

    @Test
    void getActiveIncidentsIncludesOnlyCurrentScheduledMaintenanceWindows() {
        StatusIncidentRepository repository = mock(StatusIncidentRepository.class);
        StatusIncidentService service = new StatusIncidentService(repository, mock(AdminAuditLogger.class));
        LocalDateTime now = LocalDateTime.now();

        StatusIncident currentMaintenance = maintenance(
                "current-maintenance",
                now.minusMinutes(10),
                now.plusMinutes(20)
        );
        StatusIncident futureMaintenance = maintenance(
                "future-maintenance",
                now.plusHours(1),
                now.plusHours(2)
        );
        StatusIncident expiredMaintenance = maintenance(
                "expired-maintenance",
                now.minusHours(2),
                now.minusHours(1)
        );

        when(repository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(
                futureMaintenance,
                currentMaintenance,
                expiredMaintenance
        ));

        List<StatusIncidentView> active = service.getActiveIncidents();
        List<StatusIncidentView> scheduled = service.getScheduledMaintenances();

        assertEquals(1, active.size());
        assertEquals("current-maintenance", active.getFirst().id());
        assertEquals(1, scheduled.size());
        assertEquals("future-maintenance", scheduled.getFirst().id());
        assertTrue(active.stream().noneMatch(incident -> incident.id().equals("expired-maintenance")));
        assertFalse(scheduled.stream().anyMatch(incident -> incident.id().equals("expired-maintenance")));
    }

    private static StatusIncident maintenance(String id, LocalDateTime start, LocalDateTime end) {
        StatusIncident incident = new StatusIncident();
        incident.setId(id);
        incident.setKind(StatusIncidentKind.MAINTENANCE);
        incident.setState(StatusIncidentState.SCHEDULED);
        incident.setImpact(SystemStatus.DEGRADED);
        incident.setTitle(id);
        incident.setAffectedServices(List.of("api"));
        incident.setScheduledStart(start);
        incident.setScheduledEnd(end);
        incident.setCreatedAt(start.minusHours(1));
        incident.setUpdatedAt(start.minusHours(1));
        return incident;
    }
}
