package net.modtale.service.analytics;

import net.modtale.model.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ScoringServiceTest {

    @Test
    void ensureScoresSkipsNullEmptyAndZeroDownloadProjects() {
        ScoringService scoringService = spy(new ScoringService());
        doNothing().when(scoringService).updateProjectScores();

        Project zeroDownloads = new Project();
        zeroDownloads.setDownloadCount(0);

        scoringService.ensureScores(null);
        scoringService.ensureScores(List.of());
        scoringService.ensureScores(List.of(zeroDownloads));

        verify(scoringService, never()).updateProjectScores();
    }

    @Test
    void ensureScoresTriggersRecalculationWhenAnyScoredProjectLooksMissing() {
        ScoringService scoringService = spy(new ScoringService());
        doNothing().when(scoringService).updateProjectScores();

        Project complete = new Project();
        complete.setDownloadCount(25);
        complete.setPopularScore(30.0);
        complete.setRelevanceScore(12.0);
        complete.setTrendScore(4);

        Project missing = new Project();
        missing.setDownloadCount(10);
        missing.setPopularScore(0.0);
        missing.setRelevanceScore(7.0);
        missing.setTrendScore(2);

        scoringService.ensureScores(List.of(complete));
        verify(scoringService, never()).updateProjectScores();

        scoringService.ensureScores(List.of(complete, missing));
        verify(scoringService, times(1)).updateProjectScores();
    }
}
