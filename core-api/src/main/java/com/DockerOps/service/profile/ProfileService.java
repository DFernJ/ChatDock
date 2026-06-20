package com.DockerOps.service.profile;

import com.DockerOps.model.users.Code;
import com.DockerOps.model.users.User;
import com.DockerOps.model.users.enums.CodeType;
import com.DockerOps.repository.users.CodeRepository;
import com.DockerOps.repository.users.UserRepository;
import com.DockerOps.service.users.CodeGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class ProfileService {

    private static final Duration DISCORD_CODE_TTL = Duration.ofMinutes(10);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CodeRepository codeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private GitHubOAuthService gitHubOAuthService;
    @Autowired
    private CodeGeneratorService codeGeneratorService;

    public User updateUsername(UUID userId, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        User user = userRepository.findById(userId).orElseThrow();
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            return null;
        }
        user.setUsername(username);
        return userRepository.save(user);
    }

    public boolean updatePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPassword_hash())) {
            return false;
        }
        user.setPassword_hash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

    @Transactional
    public Code generateDiscordLinkCode(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        codeRepository.deleteByUserAndCodeType(user, CodeType.DISCORD);
        Code code = Code.builder()
                .id(UUID.randomUUID())
                .code(codeGeneratorService.generateUniqueCode())
                .user(user)
                .remainUses(1)
                .codeType(CodeType.DISCORD)
                .expiresAt(Instant.now().plus(DISCORD_CODE_TTL))
                .build();
        return codeRepository.save(code);
    }

    public void unlinkDiscord(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setDiscordId(null);
        user.setDiscordUsername(null);
        userRepository.save(user);
    }

    public User consumeDiscordLinkCode(String rawCode, Long discordId, String discordUsername) {
        Code code = codeRepository.findByCode(rawCode)
                .filter(c -> c.getCodeType() == CodeType.DISCORD && c.getRemainUses() > 0)
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired link code."));

        User user = code.getUser();
        if (!discordId.equals(user.getDiscordId()) && userRepository.existsByDiscordId(discordId)) {
            codeRepository.delete(code);
            throw new IllegalArgumentException("This Discord account is already linked to another user.");
        }

        user.setDiscordId(discordId);
        user.setDiscordUsername(discordUsername);
        userRepository.save(user);
        codeRepository.delete(code);

        return user;
    }

    public void linkGithub(UUID userId, long githubId, String login, String accessToken) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!Long.valueOf(githubId).equals(user.getGithubId()) && userRepository.existsByGithubId(githubId)) {
            throw new IllegalArgumentException("This GitHub account is already linked to another user.");
        }
        user.setGithubId(githubId);
        user.setGithubUsername(login);
        user.setGithubAccessToken(accessToken);
        userRepository.save(user);
    }

    public void unlinkGithub(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getGithubAccessToken() != null) {
            try {
                gitHubOAuthService.revokeAccess(user.getGithubAccessToken());
            } catch (Exception e) {
                log.warn("Could not revoke GitHub authorization for user {}", userId, e);
            }
        }
        user.setGithubId(null);
        user.setGithubUsername(null);
        user.setGithubAccessToken(null);
        userRepository.save(user);
    }
}
