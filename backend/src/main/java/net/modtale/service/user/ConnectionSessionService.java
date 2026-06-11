package net.modtale.service.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.exception.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Service;

@Service
public class ConnectionSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionSessionService.class);

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    public ConnectionSessionService(OAuth2AuthorizedClientRepository authorizedClientRepository) {
        this.authorizedClientRepository = authorizedClientRepository;
    }

    public String loadAuthorizedClientAccessToken(
            String registrationId,
            Authentication authentication,
            HttpServletRequest request,
            String failureMessage
    ) {
        try {
            OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(registrationId, authentication, request);
            if (client != null && client.getAccessToken() != null) {
                return client.getAccessToken().getTokenValue();
            }
            return null;
        } catch (RuntimeException ex) {
            throw new UpstreamServiceException(HttpStatus.INTERNAL_SERVER_ERROR, failureMessage, ex);
        }
    }

    public void clearAuthorizedClient(
            String registrationId,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            String userId
    ) {
        try {
            authorizedClientRepository.removeAuthorizedClient(registrationId, authentication, request, response);
        } catch (RuntimeException ex) {
            logger.warn("Failed to clear expired {} authorized client for user={}", registrationId, userId, ex);
        }
    }
}
