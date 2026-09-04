package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.modtale.launcher.model.project.ProjectComment;
import org.junit.jupiter.api.Test;

class NativeCommentSectionTest {

    @Test
    void externalAuthorsWithoutPlatformIdsResolveToAnEmptyIdentity() {
        assertEquals("", NativeCommentSection.identityId(
                null,
                new ProjectComment.Author(null, "External user", "https://example.com/avatar.png")
        ));
    }

    @Test
    void identityPrefersTheCommentUserIdAndFallsBackToTheAuthorId() {
        ProjectComment.Author author = new ProjectComment.Author("author-id", "Author", null);

        assertEquals("comment-user-id", NativeCommentSection.identityId("comment-user-id", author));
        assertEquals("author-id", NativeCommentSection.identityId(null, author));
    }
}
