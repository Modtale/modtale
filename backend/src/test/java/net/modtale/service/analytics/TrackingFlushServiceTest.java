package net.modtale.service.analytics;

import java.time.LocalDate;
import net.modtale.model.analytics.PlatformMonthlyStats;
import net.modtale.service.project.query.ProjectService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrackingFlushServiceTest {
    @Test
    void persistsExclusiveSourcesEvenWhenLauncherHasApiRole() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        TrackingBufferService buffer = new TrackingBufferService();
        TrackingService service = new TrackingService(buffer, new TrackingFlushService(mongo, mock(ProjectService.class), buffer));
        service.logDownload("project", null, "author", false, null);
        service.logDownload("project", null, "author", true, null);
        service.logDownload("project", null, "author", true, null, true);
        service.logDownload("project", null, "author", false, null, true);
        // Base project counters are independent of source attribution.
        assertEquals(4, buffer.drainMetricIncrements().downloads().get("project"));
        service.flushAnalyticsBuffer();

        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).upsert(any(Query.class), update.capture(), eq(PlatformMonthlyStats.class));
        Document increments = (Document) update.getValue().getUpdateObject().get("$inc");
        assertEquals(4, increments.get("totalDownloads"));
        assertEquals(1, increments.get("apiDownloads"));
        assertEquals(1, increments.get("frontendDownloads"));
        assertEquals(2, increments.get("launcherDownloads"));
        assertEquals(2, increments.get("days." + LocalDate.now().getDayOfMonth() + ".l"));
    }
}
