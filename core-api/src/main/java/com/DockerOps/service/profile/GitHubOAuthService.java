package com.DockerOps.service.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Handles the GitHub App "user authorization" (OAuth) flow used to identify which
 * GitHub account belongs to which operator. Only reads the user's public identity
 * (id, login) - no repository access is requested.
 */
@Service
public class GitHubOAuthService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String GRANT_URL = "https://api.github.com/applications/{client_id}/grant";

    @Value("${app.github.client-id}")
    private String clientId;

    @Value("${app.github.client-secret}")
    private String clientSecret;

    @Value("${app.public-url}")
    private String publicUrl;

    private final RestClient restClient = RestClient.create();

    public String callbackUrl() {
        return publicUrl + "/api/profile/github/callback";
    }

    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("state", state)
                .build()
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

    /**
     * Revokes the app's authorization grant for the given access token, so GitHub
     * shows the consent screen again next time the user starts the link flow.
     */
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

    public record GitHubUser(long id, String login, String accessToken) {}
}
