package net.modtale.service.user.organization;

import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import org.springframework.stereotype.Service;

@Service
public class OrganizationConnectionService {

    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;
    private final ConnectedAccountMutationService connectedAccountMutationService;

    public OrganizationConnectionService(
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService,
            ConnectedAccountMutationService connectedAccountMutationService
    ) {
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
        this.connectedAccountMutationService = connectedAccountMutationService;
    }

    public User requireConnectionManagedOrganization(String orgId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_CONNECTION_MANAGE,
                "You do not have permission to manage connected accounts for this organization."
        );
        return org;
    }

    public void unlinkOrgAccount(String orgId, String provider, User requester) {
        User org = requireConnectionManagedOrganization(orgId, requester);
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);
        if (connectedAccountMutationService.unlink(org, targetProvider)) {
            userRepository.save(org);
        }
    }

    public void toggleOrgConnectionVisibility(String orgId, String provider, User requester) {
        OAuthProvider targetProvider = OAuthProvider.fromString(provider);
        if (targetProvider == OAuthProvider.GOOGLE) {
            throw new InvalidOrganizationRequestException("Google accounts cannot be made visible.");
        }

        User org = requireConnectionManagedOrganization(orgId, requester);
        if (connectedAccountMutationService.toggleVisibility(org, targetProvider)) {
            userRepository.save(org);
        }
    }
}
