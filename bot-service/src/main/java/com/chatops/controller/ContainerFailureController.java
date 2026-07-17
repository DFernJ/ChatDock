package com.chatops.controller;

import com.chatops.dto.ContainerFailureEvent;
import com.chatops.service.ContainerFailureNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class ContainerFailureController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${security.internal-token}")
    private String internalToken;

    private final ContainerFailureNotifier containerFailureNotifier;

    public ContainerFailureController(ContainerFailureNotifier containerFailureNotifier) {
        this.containerFailureNotifier = containerFailureNotifier;
    }

    @PostMapping("/container-failure")
    public ResponseEntity<?> containerFailure(@RequestHeader(INTERNAL_TOKEN_HEADER) String token,
                                               @RequestBody ContainerFailureEvent event) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        containerFailureNotifier.notifyFailure(event);
        return ResponseEntity.ok().build();
    }
}
