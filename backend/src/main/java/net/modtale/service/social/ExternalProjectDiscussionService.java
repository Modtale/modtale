package net.modtale.service.social;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectCommentsDTO;
import net.modtale.model.project.Comment;
import net.modtale.model.project.ExternalProjectDiscussion;
import net.modtale.repository.project.ExternalProjectDiscussionRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.validation.SanitizationService;
import org.springframework.stereotype.Service;

@Service
public class ExternalProjectDiscussionService {
    private final ExternalProjectDiscussionRepository repository;
    private final UserRepository userRepository;
    private final SanitizationService sanitizer;

    public ExternalProjectDiscussionService(
            ExternalProjectDiscussionRepository repository,
            UserRepository userRepository,
            SanitizationService sanitizer
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.sanitizer = sanitizer;
    }

    public ProjectCommentsDTO getComments(String provider, String externalProjectId, String currentUserId) {
        ExternalProjectDiscussion discussion = repository.findById(key(provider, externalProjectId)).orElse(null);
        if (discussion == null || discussion.getComments() == null) {
            return new ProjectCommentsDTO(List.of());
        }
        return new ProjectCommentsDTO(discussion.getComments().stream()
                .sorted(Comparator.comparing(Comment::isPinned).reversed()
                        .thenComparing(Comment::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(comment -> ProjectMapper.toCommentDTO(comment, currentUserId))
                .toList());
    }

    public void addComment(String provider, String externalProjectId, String userId, String content) {
        requireUser(userId);
        ExternalProjectDiscussion discussion = discussion(provider, externalProjectId);
        comments(discussion).add(0, new Comment(userId, sanitizer.sanitizePlainText(content)));
        save(discussion);
    }

    public void editComment(String provider, String externalProjectId, String commentId, String userId, String content) {
        ExternalProjectDiscussion discussion = requiredDiscussion(provider, externalProjectId);
        Comment comment = requiredComment(discussion, commentId);
        if (!userId.equals(comment.getUserId())) {
            throw new ForbiddenOperationException("You can only edit your own comments.");
        }
        comment.setContent(sanitizer.sanitizePlainText(content));
        comment.setUpdatedAt(LocalDateTime.now().toString());
        save(discussion);
    }

    public void deleteComment(
            String provider,
            String externalProjectId,
            String commentId,
            String userId,
            boolean moderator
    ) {
        ExternalProjectDiscussion discussion = requiredDiscussion(provider, externalProjectId);
        Comment comment = requiredComment(discussion, commentId);
        if (!moderator && !userId.equals(comment.getUserId())) {
            throw new ForbiddenOperationException("You can only delete your own comments.");
        }
        comments(discussion).remove(comment);
        save(discussion);
    }

    public void voteComment(
            String provider,
            String externalProjectId,
            String commentId,
            String userId,
            boolean upvote
    ) {
        ExternalProjectDiscussion discussion = requiredDiscussion(provider, externalProjectId);
        Comment comment = requiredComment(discussion, commentId);
        if (upvote) {
            if (!comment.getUpvotes().remove(userId)) comment.getUpvotes().add(userId);
            comment.getDownvotes().remove(userId);
        } else {
            if (!comment.getDownvotes().remove(userId)) comment.getDownvotes().add(userId);
            comment.getUpvotes().remove(userId);
        }
        save(discussion);
    }

    public void setPinned(String provider, String externalProjectId, String commentId, boolean pinned) {
        ExternalProjectDiscussion discussion = requiredDiscussion(provider, externalProjectId);
        requiredComment(discussion, commentId).setPinned(pinned);
        save(discussion);
    }

    private ExternalProjectDiscussion discussion(String provider, String externalProjectId) {
        String id = key(provider, externalProjectId);
        return repository.findById(id).orElseGet(() -> new ExternalProjectDiscussion("CURSEFORGE", externalProjectId));
    }

    private ExternalProjectDiscussion requiredDiscussion(String provider, String externalProjectId) {
        return repository.findById(key(provider, externalProjectId))
                .orElseThrow(() -> new ResourceNotFoundException("Discussion not found."));
    }

    private static Comment requiredComment(ExternalProjectDiscussion discussion, String commentId) {
        return comments(discussion).stream()
                .filter(comment -> commentId.equals(comment.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found."));
    }

    private static List<Comment> comments(ExternalProjectDiscussion discussion) {
        if (discussion.getComments() == null) discussion.setComments(new ArrayList<>());
        return discussion.getComments();
    }

    private void save(ExternalProjectDiscussion discussion) {
        discussion.setUpdatedAt(LocalDateTime.now());
        repository.save(discussion);
    }

    private void requireUser(String userId) {
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User not found.");
    }

    static String key(String provider, String externalProjectId) {
        if (!"curseforge".equalsIgnoreCase(provider == null ? "" : provider.trim())) {
            throw new InvalidProjectRequestException("Unsupported external project provider.");
        }
        try {
            long id = Long.parseLong(externalProjectId);
            if (id <= 0) throw new NumberFormatException();
            return "CURSEFORGE:" + id;
        } catch (NumberFormatException exception) {
            throw new InvalidProjectRequestException("CurseForge project IDs must be positive numbers.");
        }
    }
}
