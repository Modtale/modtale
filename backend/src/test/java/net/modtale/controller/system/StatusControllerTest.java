package net.modtale.controller.system;

import net.modtale.model.dto.response.system.SystemStatusView;
import net.modtale.model.system.StatusHistory;
import net.modtale.repository.system.StatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;
import java.util.List;

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
    private StatusHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        s3Client = mock(S3Client.class);
        historyRepository = mock(StatusHistoryRepository.class);
        controller = new StatusController(mongoTemplate, s3Client, historyRepository);
    }

    @Test
    void getSystemStatusReturnsTheLatestEntryAndHistorySeries() {
        StatusHistory latest = history("operational", 20, 10, 5, LocalDateTime.now().minusMinutes(5));
        StatusHistory previous = history("operational", 22, 12, 6, LocalDateTime.now().minusHours(1));

        when(historyRepository.findTopByOrderByTimestampDesc()).thenReturn(latest);
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(List.of(previous, latest));

        var response = controller.getSystemStatus("24h");

        assertEquals(200, response.getStatusCode().value());
        SystemStatusView body = response.getBody();
        assertEquals("operational", body.overall());
        assertEquals(3, body.services().size());
        assertEquals(2, body.history().size());
    }

    @Test
    void getSystemStatusPerformsAHealthCheckWhenHistoryIsEmpty() {
        StatusHistory saved = history("operational", 15, 8, 4, LocalDateTime.now());

        when(historyRepository.findTopByOrderByTimestampDesc()).thenReturn(null);
        when(historyRepository.save(any(StatusHistory.class))).thenReturn(saved);
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(List.of(saved));

        var response = controller.getSystemStatus("24h");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("operational", response.getBody().overall());
        verify(mongoTemplate).executeCommand("{ ping: 1 }");
        verify(historyRepository).save(any(StatusHistory.class));
        assertFalse(response.getBody().history().isEmpty());
    }

    private static StatusHistory history(String overall, int api, int db, int storage, LocalDateTime timestamp) {
        StatusHistory history = new StatusHistory(api, db, storage, overall);
        ReflectionTestUtils.setField(history, "timestamp", timestamp);
        return history;
    }
}
