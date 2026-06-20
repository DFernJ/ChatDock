package com.DockerOps.controller;

import com.DockerOps.dto.request.UpdatePasswordRequest;
import com.DockerOps.dto.request.UpdateProfileRequest;
import com.DockerOps.dto.response.DiscordLinkCodeResponse;
import com.DockerOps.dto.response.ProfileResponse;
import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.service.profile.GitHubOAuthService;
import com.DockerOps.service.profile.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String GITHUB_STATE_COOKIE = "github_oauth_state";
    private static final String GITHUB_ORIGIN_COOKIE = "github_oauth_origin";

    @Autowired
    private ProfileService profileService;
    @Autowired
    private GitHubOAuthService gitHubOAuthService;

    @Value("${app.security.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.security.cors.allow-origin}")
    private String[] allowedOrigins;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        return ResponseEntity.ok(ProfileResponse.from(currentUser(authentication)));
    }

    @PatchMapping
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        User updated = profileService.updateUsername(currentUser(authentication).getId(), request.username());
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already taken.");
        }
        return ResponseEntity.ok(ProfileResponse.from(updated));
    }

    @PatchMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody UpdatePasswordRequest request, Authentication authentication) {
        if (request.newPassword() == null || request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        boolean updated = profileService.updatePassword(currentUser(authentication).getId(), request.currentPassword(), request.newPassword());
        if (!updated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Current password is incorrect.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/discord/link-code")
    public ResponseEntity<DiscordLinkCodeResponse> createDiscordLinkCode(Authentication authentication) {
        Code code = profileService.generateDiscordLinkCode(currentUser(authentication).getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(DiscordLinkCodeResponse.from(code));
    }

    @DeleteMapping("/discord")
    public ResponseEntity<?> unlinkDiscord(Authentication authentication) {
        profileService.unlinkDiscord(currentUser(authentication).getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/github/authorize")
    public void authorizeGithub(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        setGithubCookie(response, GITHUB_STATE_COOKIE, state, Duration.ofMinutes(10));
        setGithubCookie(response, GITHUB_ORIGIN_COOKIE, resolveFrontendOrigin(request), Duration.ofMinutes(10));
        response.sendRedirect(gitHubOAuthService.buildAuthorizeUrl(state));
    }

    @GetMapping("/github/callback")
    public void githubCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String state,
                                @CookieValue(value = GITHUB_STATE_COOKIE, required = false) String expectedState,
                                @CookieValue(value = GITHUB_ORIGIN_COOKIE, required = false) String origin,
                                Authentication authentication,
                                HttpServletResponse response) throws IOException {
        setGithubCookie(response, GITHUB_STATE_COOKIE, "", Duration.ZERO);
        setGithubCookie(response, GITHUB_ORIGIN_COOKIE, "", Duration.ZERO);
        String redirectTo = (origin != null ? origin : allowedOrigins[0]) + "/profile";

        if (code == null || state == null || expectedState == null || !state.equals(expectedState)) {
            response.sendRedirect(redirectTo + "?github=error");
            return;
        }
        try {
            GitHubOAuthService.GitHubUser githubUser = gitHubOAuthService.exchangeCodeForUser(code);
            profileService.linkGithub(currentUser(authentication).getId(), githubUser.id(), githubUser.login(), githubUser.accessToken());
            response.sendRedirect(redirectTo + "?github=linked");
        } catch (Exception e) {
            response.sendRedirect(redirectTo + "?github=error");
        }
    }

    @DeleteMapping("/github")
    public ResponseEntity<?> unlinkGithub(Authentication authentication) {
        profileService.unlinkGithub(currentUser(authentication).getId());
        return ResponseEntity.noContent().build();
    }

    private String resolveFrontendOrigin(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer != null) {
            for (String allowed : allowedOrigins) {
                if (referer.startsWith(allowed)) {
                    return allowed;
                }
            }
        }
        return allowedOrigins[0];
    }

    private void setGithubCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .maxAge(maxAge)
                .path("/api/profile/github")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
