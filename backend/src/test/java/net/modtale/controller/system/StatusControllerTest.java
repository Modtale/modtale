package net.modtale.controller.system;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.model.dto.response.system.SystemStatusView;
import net.modtale.model.system.StatusHistory;
import net.modtale.model.system.SystemStatus;
import net.modtale.repository.system.StatusHistoryRepository;
import net.modtale.service.system.StatusDiscordNotifierService;
import net.modtale.service.system.StatusIncidentService;
import net.modtale.service.system.StatusSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusControllerTest {

    private StatusController controller;
    private MongoTemplate mongoTemplate;
    private S3Client s3Client;
    private AppR2Properties r2Properties;
    private StatusHistoryRepository historyRepository;
    private StatusDiscordNotifierService statusDiscordNotifierService;
    private StatusIncidentService statusIncidentService;
    private StatusSnapshotService statusSnapshotService;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        s3Client = mock(S3Client.class);
        r2Properties = new AppR2Properties("modtale-test", "access-key", "secret-key", "https://example.com", "");
        historyRepository = mock(StatusHistoryRepository.class);
        statusDiscordNotifierService = mock(StatusDiscordNotifierService.class);
        statusIncidentService = mock(StatusIncidentService.class);
        when(statusIncidentService.getActiveIncidents()).thenReturn(List.of());
        when(statusIncidentService.getScheduledMaintenances()).thenReturn(List.of());
        when(statusIncidentService.getIncidentHistory()).thenReturn(List.of());
        statusSnapshotService = new StatusSnapshotService(mongoTemplate, s3Client, r2Properties, historyRepository, statusDiscordNotifierService, statusIncidentService, false);
        controller = new StatusController(statusSnapshotService);
    }

    @Test
    void getSystemStatusReturnsTheLatestEntryAndHistorySeries() {
        StatusHistory latest = history(SystemStatus.OPERATIONAL, 20, 10, 5, LocalDateTime.now().minusMinutes(5));
        StatusHistory previous = history(SystemStatus.OPERATIONAL, 22, 12, 6, LocalDateTime.now().minusHours(1));

        when(historyRepository.findTopByOrderByTimestampDesc()).thenReturn(latest);
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(List.of(previous, latest));

        var response = controller.getSystemStatus("24h");

        assertEquals(200, response.getStatusCode().value());
        SystemStatusView body = response.getBody();
        assertEquals(SystemStatus.OPERATIONAL, body.overall());
        assertEquals(3, body.services().size());
        assertEquals(2, body.history().size());
    }

    @Test
    void getSystemStatusPerformsAHealthCheckWhenHistoryIsEmpty() {
        StatusHistory saved = history(SystemStatus.OPERATIONAL, 15, 8, 4, LocalDateTime.now());

        when(historyRepository.findTopByOrderByTimestampDesc()).thenReturn(null);
        when(historyRepository.save(any(StatusHistory.class))).thenReturn(saved);
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(List.of(saved));

        var response = controller.getSystemStatus("24h");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(SystemStatus.OPERATIONAL, response.getBody().overall());
        verify(mongoTemplate).executeCommand("{ ping: 1 }");
        verify(historyRepository).save(any(StatusHistory.class));
        assertFalse(response.getBody().history().isEmpty());
    }

    private static StatusHistory history(SystemStatus overall, int api, int db, int storage, LocalDateTime timestamp) {
        StatusHistory history = new StatusHistory(api, db, storage, overall);
        ReflectionTestUtils.setField(history, "timestamp", timestamp);
        return history;
    }
}
