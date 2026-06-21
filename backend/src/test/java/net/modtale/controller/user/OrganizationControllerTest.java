package net.modtale.controller.user;

import net.modtale.model.dto.request.organization.CreateOrganizationRequest;
import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.user.organization.OrganizationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationControllerTest {

    private OrganizationController controller;
    private OrganizationApplicationService organizationApplicationService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        organizationApplicationService = mock(OrganizationApplicationService.class);
        accountService = mock(AccountService.class);
        controller = new OrganizationController(organizationApplicationService, accountService);
    }

    @Test
    void createOrganizationDelegatesUsingRequiredCurrentUser() {
        User currentUser = user("user-1");
        CreateOrganizationRequest requestPayload = new CreateOrganizationRequest();
        requestPayload.setName("Skyforge Studios");
        UserDTO createdOrg = new UserDTO();
        createdOrg.setId("org-1");
        createdOrg.setUsername("Skyforge Studios");

        when(accountService.requireCurrentUser("creating an organization")).thenReturn(currentUser);
        when(organizationApplicationService.createOrganization("Skyforge Studios", currentUser)).thenReturn(createdOrg);

        var response = controller.createOrganization(requestPayload);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("org-1", response.getBody().getId());
        verify(organizationApplicationService).createOrganization("Skyforge Studios", currentUser);
    }

    @Test
    void uploadOrgAvatarReturnsTypedUrlResponse() throws Exception {
        User currentUser = user("user-1");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        when(accountService.requireCurrentUser("uploading an organization avatar")).thenReturn(currentUser);
        when(organizationApplicationService.uploadOrganizationAvatar("org-1", file, currentUser))
                .thenReturn(new ResourceUrlResponse("https://cdn.modtale.net/orgs/org-1/avatar.png"));

        var response = controller.uploadOrgAvatar("org-1", file);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("https://cdn.modtale.net/orgs/org-1/avatar.png", response.getBody().url());
    }

    @Test
    void removeOrgMemberDelegatesToApplicationService() {
        User currentUser = user("user-1");

        when(accountService.requireCurrentUser("removing an organization member")).thenReturn(currentUser);

        var response = controller.removeOrgMember("org-1", "user-2");

        assertEquals(200, response.getStatusCode().value());
        verify(organizationApplicationService).removeOrganizationMember("org-1", "user-2", currentUser);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
