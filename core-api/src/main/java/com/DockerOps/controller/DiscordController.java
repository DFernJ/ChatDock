package com.DockerOps.controller;

import com.DockerOps.dto.request.DiscordConsumeLinkRequest;
import com.DockerOps.dto.response.UserResponse;
import com.DockerOps.model.users.User;
import com.DockerOps.service.profile.ProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/discord")
public class DiscordController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${app.security.internal-token}")
    private String internalToken;

    @Autowired
    private ProfileService profileService;

    @PostMapping("/link")
    public ResponseEntity<?> link(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                   @RequestBody DiscordConsumeLinkRequest request) {
        if (!internalToken.equals(token)) {
            log.warn("Rejected Discord link request for discordId={}: invalid internal token", request.discordId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Linking discordId={}, username={} using a link code", request.discordId(), request.discordUsername());
        User user = profileService.consumeDiscordLinkCode(request.code(), request.discordId(), request.discordUsername());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/whoami")
    public ResponseEntity<?> whoami(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                     @RequestParam Long discordId) {
        if (!internalToken.equals(token)) {
            log.warn("Rejected whoami request for discordId={}: invalid internal token", discordId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Fetching whoami for discordId={}", discordId);
        return profileService.findByDiscordId(discordId)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejected request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
