package net.modtale.service.analytics;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsEligibilityServiceTest {

    private UserRepository userRepository;
    private AnalyticsEligibilityService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AnalyticsEligibilityService(userRepository);
    }

    @Test
    void countsAnonymousEngagement() {
        assertTrue(service.shouldCountProjectEngagement(project("author-1"), null));
    }

    @Test
    void excludesProjectOwner() {
        assertFalse(service.shouldCountProjectEngagement(project("author-1"), user("author-1")));
    }

    @Test
    void excludesProjectTeamMembers() {
        Project project = project("author-1");
        project.setTeamMembers(List.of(new Project.ProjectMember("user-2", "role-1")));

        assertFalse(service.shouldCountProjectEngagement(project, user("user-2")));
    }

    @Test
    void excludesOrganizationMembersForOrganizationOwnedProjects() {
        Project project = project("org-1");
        User organization = new User();
        organization.setId("org-1");
        organization.setAccountType(User.AccountType.ORGANIZATION);
        organization.setOrganizationMembers(List.of(new User.OrganizationMember("user-2", "role-1")));

        when(userRepository.findById("org-1")).thenReturn(Optional.of(organization));

        assertFalse(service.shouldCountProjectEngagement(project, user("user-2")));
    }

    @Test
    void countsNonAffiliatedUsers() {
        Project project = project("org-1");
        User organization = new User();
        organization.setId("org-1");
        organization.setAccountType(User.AccountType.ORGANIZATION);
        organization.setOrganizationMembers(List.of(new User.OrganizationMember("user-3", "role-1")));

        when(userRepository.findById("org-1")).thenReturn(Optional.of(organization));

        assertTrue(service.shouldCountProjectEngagement(project, user("user-2")));
    }

    private static Project project(String authorId) {
        Project project = new Project();
        project.setAuthorId(authorId);
        return project;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
