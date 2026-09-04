package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import net.modtale.launcher.model.project.ProjectComment;
import org.junit.jupiter.api.Test;

class CurseForgeCommentsLiveContractTest {

    @Test
    void curseForgeServesDistinctReadOnlyCommentPagesDirectlyToTheLauncher() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("CURSEFORGE_LIVE_TESTS")),
                "Set CURSEFORGE_LIVE_TESTS=true to run the direct CurseForge comments contract.");
        CurseForgeCommentsClient client = new CurseForgeCommentsClient(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build());

        var first = client.getComments(1430352, 0);
        var second = client.getComments(1430352, 1);

        assertFalse(first.comments().isEmpty());
        assertFalse(second.comments().isEmpty());
        assertTrue(first.totalCount() > count(first.comments()));
        assertTrue(first.hasMore());
        Set<String> firstIds = new HashSet<>(first.comments().stream().map(ProjectComment::id).toList());
        assertTrue(second.comments().stream().map(ProjectComment::id).noneMatch(firstIds::contains));
        assertTrue(first.comments().stream().allMatch(CurseForgeCommentsLiveContractTest::isReadOnlyTree));
        assertTrue(second.comments().stream().allMatch(CurseForgeCommentsLiveContractTest::isReadOnlyTree));
    }

    private static boolean isReadOnlyTree(ProjectComment comment) {
        return comment.readOnly() && comment.replies().stream().allMatch(CurseForgeCommentsLiveContractTest::isReadOnlyTree);
    }

    private static long count(java.util.List<ProjectComment> comments) {
        return comments.stream().mapToLong(comment -> 1 + count(comment.replies())).sum();
    }
}
