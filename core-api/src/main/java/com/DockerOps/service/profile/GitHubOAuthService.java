package com.DockerOps.service.profile;

import com.DockerOps.dto.response.GitHubRepoDTO;
import com.DockerOps.model.users.User;
import com.DockerOps.repository.users.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GitHubOAuthService {

    private static final String INSTALL_URL = "https://github.com/apps/{appSlug}/installations/new";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String GRANT_URL = "https://api.github.com/applications/{client_id}/grant";
    private static final String REPOS_URL = "https://api.github.com/user/repos?sort=updated&per_page=100";
    private static final String INSTALLATION_REPOS_URL = "https://api.github.com/installation/repositories?per_page=100";
    private static final String INSTALLATION_TOKEN_URL = "https://api.github.com/app/installations/{installationId}/access_tokens";
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("^[\\w.-]+/[\\w.-]+$");

    @Value("${app.github.client-id}")
    private String clientId;

    @Value("${app.github.client-secret}")
    private String clientSecret;

    @Value("${app.github.app-slug}")
    private String appSlug;

    @Value("${app.github.app-id}")
    private String appId;

    @Value("${app.github.private-key}")
    private String privateKey;

    @Value("${app.public-url}")
    private String publicUrl;

    private final UserRepository userRepository;
    private final RestClient restClient = RestClient.create();

    public GitHubOAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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

    public List<GitHubRepoDTO> listRepositories(User user) {
        try {
            return toRepoDTOs(restClient.get()
                    .uri(REPOS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getGithubAccessToken())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(GitHubRepoResponse[].class));
        } catch (HttpClientErrorException.Unauthorized e) {
            if (user.getInstallationId() == null) {
                throw e;
            }
            log.info("GitHub access token expired for userId={}, renewing via installation token", user.getId());
            String renewedToken = renewInstallationToken(user.getInstallationId());
            user.setGithubAccessToken(renewedToken);
            userRepository.save(user);

            InstallationRepoResponse response = restClient.get()
                    .uri(INSTALLATION_REPOS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + renewedToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(InstallationRepoResponse.class);
            return toRepoDTOs(response != null ? response.repositories() : null);
        }
    }

    private List<GitHubRepoDTO> toRepoDTOs(GitHubRepoResponse[] repos) {
        if (repos == null) return List.of();
        return Arrays.stream(repos)
                .map(r -> new GitHubRepoDTO(r.fullName(), r.defaultBranch(), r.isPrivate()))
                .toList();
    }

    private String renewInstallationToken(long installationId) {
        InstallationTokenResponse response = restClient.post()
                .uri(INSTALLATION_TOKEN_URL, installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buildAppJwt())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(InstallationTokenResponse.class);
        if (response == null || response.token() == null) {
            throw new IllegalStateException("Could not renew the GitHub installation access token.");
        }
        return response.token();
    }

    private String buildAppJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(appId)
                .setIssuedAt(Date.from(now.minusSeconds(60)))
                .setExpiration(Date.from(now.plusSeconds(600)))
                .signWith(loadPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey() {
        try {
            String base64 = privateKey
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] pkcs1 = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs1ToPkcs8(pkcs1)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid GitHub App private key.", e);
        }
    }

    private byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        int length = pkcs1.length + 22;
        byte[] header = {
                0x30, (byte) 0x82, (byte) ((length >> 8) & 0xff), (byte) (length & 0xff),
                0x02, 0x01, 0x00,
                0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00,
                0x04, (byte) 0x82, (byte) ((pkcs1.length >> 8) & 0xff), (byte) (pkcs1.length & 0xff)
        };
        byte[] pkcs8 = new byte[header.length + pkcs1.length];
        System.arraycopy(header, 0, pkcs8, 0, header.length);
        System.arraycopy(pkcs1, 0, pkcs8, header.length, pkcs1.length);
        return pkcs8;
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

    private record InstallationTokenResponse(String token) {}

    private record InstallationRepoResponse(GitHubRepoResponse[] repositories) {}

    public record GitHubUser(long id, String login, String accessToken) {}
}
