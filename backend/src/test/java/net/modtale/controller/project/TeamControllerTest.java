package net.modtale.controller.project;

import java.util.EnumSet;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.project.team.TeamService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerTest {

    private TeamController controller;
    private TeamService teamService;
    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        accountService = mock(AccountService.class);
        controller = new TeamController(teamService, accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void inviteContributorRouteDelegatesUsingTheCurrentUser() throws Exception {
        User user = user("owner-1");
        when(accountService.requireCurrentUser("inviting a project contributor")).thenReturn(user);

        mockMvc.perform(post("/api/v1/projects/project-1/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-2\",\"roleId\":\"role-1\"}"))
                .andExpect(status().isOk());

        verify(teamService).inviteContributor("project-1", "user-2", "role-1", user);
    }

    @Test
    void createProjectRoleRouteReturnsTheUpdatedProject() throws Exception {
        User user = user("owner-1");
        Project updated = new Project();
        updated.setId("project-1");
        when(accountService.requireCurrentUser("creating a project role")).thenReturn(user);
        when(teamService.createProjectRole(
                "project-1",
                "Writer",
                "#112233",
                EnumSet.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA),
                user
        )).thenReturn(updated);

        mockMvc.perform(post("/api/v1/projects/project-1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Writer\",\"color\":\"#112233\",\"permissions\":[\"PROJECT_EDIT_METADATA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("project-1"));

        verify(teamService).createProjectRole(
                "project-1",
                "Writer",
                "#112233",
                EnumSet.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA),
                user
        );
    }

    @Test
    void acceptInviteRouteUsesTheAuthenticatedUser() throws Exception {
        User user = user("user-2");
        when(accountService.requireCurrentUser("accepting a project invite")).thenReturn(user);

        mockMvc.perform(post("/api/v1/projects/project-1/invite/accept"))
                .andExpect(status().isOk());

        verify(teamService).acceptInvite("project-1", "user-2");
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
