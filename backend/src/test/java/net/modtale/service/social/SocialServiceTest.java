package net.modtale.service.social;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.security.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialServiceTest {

    private SocialService socialService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        socialService = new SocialService(
                mock(ProjectRepository.class),
                userRepository,
                mock(ProjectService.class),
                mock(NotificationService.class),
                mock(SanitizationService.class),
                mock(MongoTemplate.class),
                mock(ScoringService.class)
        );
    }

    @Test
    void followUserRejectsMissingTargetsWithNotFound() {
        when(userRepository.findById("target-1")).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> socialService.followUser("current-1", "target-1")
        );

        assertEquals("We couldn't find the user you were trying to follow.", error.getMessage());
    }

    @Test
    void followUserRejectsFollowingYourself() {
        net.modtale.model.user.User user = new net.modtale.model.user.User();
        user.setId("user-1");
        user.setUsername("ada");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        ForbiddenOperationException error = assertThrows(
                ForbiddenOperationException.class,
                () -> socialService.followUser("user-1", "user-1")
        );

        assertEquals("You cannot follow your own account.", error.getMessage());
    }
}
