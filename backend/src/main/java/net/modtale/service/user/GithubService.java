package net.modtale.service.user;

import net.modtale.model.user.GitRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GithubService {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<GitRepository> getUserRepos(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Collections.emptyList();
        }

        List<GitRepository> allRepos = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            while (hasMore) {
                ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                        "https://api.github.com/user/repos?sort=updated&per_page=100&page=" + page,
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

                List<Map<String, Object>> pageData = response.getBody();

                if (pageData != null && !pageData.isEmpty()) {
                    for (Map<String, Object> data : pageData) {
                        GitRepository repo = new GitRepository();

                        repo.setName((String) data.get("full_name"));

                        repo.setUrl((String) data.get("html_url"));

                        repo.setDescription((String) data.get("description"));

                        Object privateObj = data.get("private");
                        if (privateObj instanceof Boolean) {
                            repo.setPrivate((Boolean) privateObj);
                        }

                        allRepos.add(repo);
                    }

                    if (pageData.size() < 100) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                } else {
                    hasMore = false;
                }
            }

            return allRepos;

        } catch (HttpClientErrorException.Unauthorized e) {
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to fetch GitHub repos: " + e.getMessage());
            return allRepos.isEmpty() ? Collections.emptyList() : allRepos;
        }
    }
}