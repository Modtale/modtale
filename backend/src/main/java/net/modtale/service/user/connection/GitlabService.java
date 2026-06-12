package net.modtale.service.user.connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.modtale.model.user.GitRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GitlabService {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<GitRepository> getUserRepos(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        List<GitRepository> allRepos = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = "https://gitlab.com/api/v4/projects?membership=true&min_access_level=30&order_by=updated_at&per_page=100&page=" + page;

            ResponseEntity<List<Map<String, Object>>> apiResponse = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            List<Map<String, Object>> pageData = apiResponse.getBody();
            if (pageData == null || pageData.isEmpty()) {
                return allRepos;
            }

            for (Map<String, Object> data : pageData) {
                GitRepository repo = new GitRepository();
                repo.setName((String) data.get("path_with_namespace"));
                repo.setUrl((String) data.get("web_url"));
                repo.setDescription((String) data.get("description"));

                Object visibility = data.get("visibility");
                if (visibility instanceof String visibilityValue) {
                    repo.setPrivate("private".equalsIgnoreCase(visibilityValue) || "internal".equalsIgnoreCase(visibilityValue));
                } else {
                    repo.setPrivate(false);
                }

                allRepos.add(repo);
            }

            if (pageData.size() < 100) {
                return allRepos;
            }
            page++;
        }
    }
}
