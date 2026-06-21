package net.modtale.service.social;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserFollowServiceTest {

    private UserRepository userRepository;
    private NotificationService notificationService;
    private MongoTemplate mongoTemplate;
    private UserFollowService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new UserFollowService(userRepository, notificationService, mongoTemplate);
    }

    @Test
    void followUserAddsRelationshipAndSendsNotificationWhenEnabled() {
        User current = user("user-1", "willow");
        current.setAvatarUrl("/avatar.png");
        User target = user("user-2", "ash");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(current));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));

        service.followUser("user-1", "user-2");

        assertEquals(List.of("user-2"), current.getFollowingIds());
        assertEquals(List.of("user-1"), target.getFollowerIds());
        verify(userRepository).save(current);
        verify(userRepository).save(target);
        verify(notificationService).sendNotifcation(
                eq(List.of("user-2")),
                eq("New Follower"),
                eq("willow started following you."),
                any(),
                eq("/avatar.png")
        );
    }

    @Test
    void followUserRejectsSelfMissingDeletedAndDoesNotDuplicateExistingFollows() {
        User current = user("user-1", "willow");
        current.setFollowingIds(new ArrayList<>(List.of("user-2")));
        User target = user("user-2", "ash");
        target.setFollowerIds(new ArrayList<>(List.of("user-1")));
        User deleted = user("deleted", "gone");
        deleted.setDeletedAt(LocalDateTime.now());

        when(userRepository.findById("user-1")).thenReturn(Optional.of(current));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(userRepository.findById("deleted")).thenReturn(Optional.of(deleted));
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ForbiddenOperationException.class, () -> service.followUser("user-1", "user-1"));
        assertThrows(ResourceNotFoundException.class, () -> service.followUser("user-1", "missing"));
        assertThrows(ResourceNotFoundException.class, () -> service.followUser("user-1", "deleted"));

        service.followUser("user-1", "user-2");

        assertEquals(List.of("user-2"), current.getFollowingIds());
        assertEquals(List.of("user-1"), target.getFollowerIds());
        verify(userRepository, never()).save(current);
        verify(notificationService, never()).sendNotifcation(any(), any(), any(), any(), any());
    }

    @Test
    void followUserHonorsFollowerNotificationPreference() {
        User current = user("user-1", "willow");
        User target = user("user-2", "ash");
        target.getNotificationPreferences().setNewFollowers(User.NotificationLevel.OFF);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(current));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));

        service.followUser("user-1", "user-2");

        verify(notificationService, never()).sendNotifcation(any(), any(), any(), any(), any());
    }

    @Test
    void unfollowUserRemovesBothSidesWhenListsExist() {
        User current = user("user-1", "willow");
        current.setFollowingIds(new ArrayList<>(List.of("user-2")));
        User target = user("user-2", "ash");
        target.setFollowerIds(new ArrayList<>(List.of("user-1")));

        when(userRepository.findById("user-1")).thenReturn(Optional.of(current));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));

        service.unfollowUser("user-1", "user-2");

        assertTrue(current.getFollowingIds().isEmpty());
        assertTrue(target.getFollowerIds().isEmpty());
        verify(userRepository).save(current);
        verify(userRepository).save(target);
    }

    @Test
    void getFollowingAndFollowersReturnActiveUsersByExpandedIds() {
        User current = user("user-1", "willow");
        current.setFollowingIds(List.of("507f1f77bcf86cd799439011", "uuid-user"));
        current.setFollowerIds(List.of("follower-1"));
        User followed = user("uuid-user", "ash");
        User follower = user("follower-1", "fern");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(current));
        when(mongoTemplate.find(any(Query.class), eq(User.class))).thenReturn(List.of(followed), List.of(follower));

        assertEquals(List.of(followed), service.getFollowing("user-1"));
        assertEquals(List.of(follower), service.getFollowers("user-1"));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).find(queryCaptor.capture(), eq(User.class));
        assertTrue(queryCaptor.getAllValues().getFirst().getQueryObject().toJson().contains("507f1f77bcf86cd799439011"));
        assertTrue(queryCaptor.getAllValues().getFirst().getFieldsObject().containsKey("username"));
    }

    @Test
    void getFollowingAndFollowersReturnEmptyForMissingDeletedOrEmptyUsers() {
        User deleted = user("deleted", "gone");
        deleted.setDeletedAt(LocalDateTime.now());
        User empty = user("empty", "empty");
        empty.setFollowingIds(List.of());
        empty.setFollowerIds(List.of());

        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        when(userRepository.findById("deleted")).thenReturn(Optional.of(deleted));
        when(userRepository.findById("empty")).thenReturn(Optional.of(empty));

        assertTrue(service.getFollowing("missing").isEmpty());
        assertTrue(service.getFollowers("deleted").isEmpty());
        assertTrue(service.getFollowing("empty").isEmpty());
        assertTrue(service.getFollowers("empty").isEmpty());
        verify(mongoTemplate, never()).find(any(Query.class), eq(User.class));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFollowingIds(new ArrayList<>());
        user.setFollowerIds(new ArrayList<>());
        return user;
    }
}
