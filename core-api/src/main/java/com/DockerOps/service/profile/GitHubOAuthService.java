package com.DockerOps.service.profile;

import com.DockerOps.dto.response.GitHubRepoDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GitHubOAuthService {

    private static final String INSTALL_URL = "https://github.com/apps/{appSlug}/installations/new";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String GRANT_URL = "https://api.github.com/applications/{client_id}/grant";
    private static final String REPOS_URL = "https://api.github.com/user/repos?sort=updated&per_page=100";
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("^[\\w.-]+/[\\w.-]+$");

    @Value("${app.github.client-id}")
    private String clientId;

    @Value("${app.github.client-secret}")
    private String clientSecret;

    @Value("${app.github.app-slug}")
    private String appSlug;

    @Value("${app.public-url}")
    private String publicUrl;

    private final RestClient restClient = RestClient.create();

    public String callbackUrl() {
        return publicUrl + "/api/profile/github/callback";
    }

    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(INSTALL_URL)
                .queryParam("state", state)
                .queryParam("redirect_uri", callbackUrl())
                .buildAndExpand(appSlug)
                .toUriString();
    }

    public GitHubUser exchangeCodeForUser(String code) {
        TokenResponse token = restClient.post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TokenRequest(clientId, clientSecret, code, callbackUrl()))
                .retrieve()
                .body(TokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new IllegalStateException("Could not exchange the GitHub authorization code.");
        }

        GitHubUserResponse user = restClient.get()
                .uri(USER_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(GitHubUserResponse.class);

        if (user == null) {
            throw new IllegalStateException("Could not fetch the GitHub user.");
        }

        return new GitHubUser(user.id(), user.login(), token.accessToken());
    }

    public List<GitHubRepoDTO> listRepositories(String accessToken) {
        GitHubRepoResponse[] repos = restClient.get()
                .uri(REPOS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(GitHubRepoResponse[].class);
        if (repos == null) return List.of();
        return Arrays.stream(repos)
                .map(r -> new GitHubRepoDTO(r.fullName(), r.defaultBranch(), r.isPrivate()))
                .toList();
    }

    public List<String> listBranches(String accessToken, String repository) {
        if (repository == null || !REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid repository");
        }
        GitHubBranchResponse[] branches = restClient.get()
                .uri("https://api.github.com/repos/" + repository + "/branches")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(GitHubBranchResponse[].class);
        if (branches == null) return List.of();
        return Arrays.stream(branches).map(GitHubBranchResponse::name).toList();
    }

    public void revokeAccess(String accessToken) {
        restClient.method(HttpMethod.DELETE)
                .uri(GRANT_URL, clientId)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RevokeRequest(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    private record RevokeRequest(@JsonProperty("access_token") String accessToken) {}

    private record TokenRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            String code,
            @JsonProperty("redirect_uri") String redirectUri
    ) {}

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    private record GitHubUserResponse(long id, String login) {}

    private record GitHubRepoResponse(
            @JsonProperty("full_name") String fullName,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("private") boolean isPrivate
    ) {}

    private record GitHubBranchResponse(String name) {}

    public record GitHubUser(long id, String login, String accessToken) {}
}
