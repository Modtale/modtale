package net.modtale.service.analytics;

import net.modtale.model.project.Project;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ScoringServiceTest {

    @Test
    void ensureScoresDoesNotTouchMongoForEmptyInput() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        ScoringService scoringService = new ScoringService(mongoTemplate);

        Project zeroDownloads = new Project();
        zeroDownloads.setDownloadCount(0);

        scoringService.ensureScores(null);
        scoringService.ensureScores(List.of());
        scoringService.ensureScores(List.of(zeroDownloads));

        verify(mongoTemplate, never()).count(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Project.class));
    }

    @Test
    void ensureScoresIsNoOpForPotentiallyStaleProjects() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        ScoringService scoringService = new ScoringService(mongoTemplate);

        Project missing = new Project();
        missing.setDownloadCount(50);

        scoringService.ensureScores(List.of(missing));

        verify(mongoTemplate, never()).count(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Project.class));
    }
}
