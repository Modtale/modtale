package net.modtale.controller.analytics;

import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.dto.response.analytics.PlatformStatsView;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.analytics.QueryService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAnalyticsControllerTest {

    private PlatformAnalyticsController controller;
    private QueryService queryService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        mongoTemplate = mock(MongoTemplate.class);
        controller = new PlatformAnalyticsController(queryService, mongoTemplate);
    }

    @Test
    void getPublicStatsReturnsTypedView() {
        when(mongoTemplate.count(any(Query.class), eq(Project.class))).thenReturn(12L);
        when(mongoTemplate.count(any(Query.class), eq(User.class))).thenReturn(34L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Project.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("totalDownloads", 56L)), new Document()));

        var response = controller.getPublicStats();

        assertEquals(200, response.getStatusCode().value());
        PlatformStatsView body = response.getBody();
        assertEquals(12L, body.totalProjects());
        assertEquals(34L, body.totalUsers());
        assertEquals(56L, body.totalDownloads());
    }

    @Test
    void getPlatformAnalyticsReturnsSummary() {
        PlatformAnalyticsSummary summary = new PlatformAnalyticsSummary();
        when(queryService.getPlatformAnalytics("30d")).thenReturn(summary);

        var response = controller.getPlatformAnalytics("30d");

        assertEquals(200, response.getStatusCode().value());
        assertSame(summary, response.getBody());
    }
}
