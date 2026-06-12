package net.modtale.service.system;

import com.mongodb.MongoException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.modtale.model.dto.response.system.SystemStatusView;
import net.modtale.model.system.StatusHistory;
import net.modtale.repository.system.StatusHistoryRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusSnapshotServiceTest {

    private MongoTemplate mongoTemplate;
    private S3Client s3Client;
    private StatusHistoryRepository historyRepository;
    private StatusSnapshotService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        s3Client = mock(S3Client.class);
        historyRepository = mock(StatusHistoryRepository.class);
        service = new StatusSnapshotService(mongoTemplate, s3Client, historyRepository);
    }

    @Test
    void getSystemStatusBuildsFromLatestHistoryAndDownsamplesThirtyDayHistory() {
        StatusHistory latest = history(12, 4, 8, "operational", LocalDateTime.now());
        List<StatusHistory> history = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            history.add(history(index, index + 1, index + 2, "operational", LocalDateTime.now().minusMinutes(600 - index)));
        }

        when(historyRepository.findTopByOrderByTimestampDesc()).thenReturn(latest);
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(history);

        SystemStatusView status = service.getSystemStatus("30d");

        assertEquals("operational", status.overall());
        assertEquals(3, status.services().size());
        assertEquals("api", status.services().getFirst().id());
        assertEquals(12, status.services().getFirst().latency());
        assertEquals(100, status.history().size());
    }

    @Test
    void refreshSnapshotsPersistsDegradedStatusWhenHealthChecksFail() {
        when(mongoTemplate.executeCommand("{ ping: 1 }")).thenThrow(new MongoException("db down"));
        when(s3Client.listBuckets()).thenReturn(ListBucketsResponse.builder().build());
        when(historyRepository.save(any(StatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.findByTimestampAfterOrderByTimestampAsc(any())).thenReturn(List.of());

        service.refreshSnapshots();

        SystemStatusView status = service.getSystemStatus("24h");
        assertEquals("degraded", status.overall());
        assertTrue(status.services().stream().anyMatch(service -> service.id().equals("database")));
        verify(historyRepository).save(any(StatusHistory.class));
    }

    private static StatusHistory history(
            int apiLatency,
            int dbLatency,
            int storageLatency,
            String overall,
            LocalDateTime timestamp
    ) {
        StatusHistory history = new StatusHistory(apiLatency, dbLatency, storageLatency, overall);
        ReflectionTestUtils.setField(history, "timestamp", timestamp);
        return history;
    }
}
