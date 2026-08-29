package com.chatops.controller;

import com.chatops.dto.ContainerFailureEvent;
import com.chatops.service.ContainerFailureNotifier;
import com.chatops.util.InternalHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Logger log = LoggerFactory.getLogger(ContainerFailureController.class);

    @Value("${security.internal-token}")
    private String internalToken;

    private final ContainerFailureNotifier containerFailureNotifier;

    public ContainerFailureController(ContainerFailureNotifier containerFailureNotifier) {
        this.containerFailureNotifier = containerFailureNotifier;
    }

    @PostMapping("/container-failure")
    public ResponseEntity<?> containerFailure(@RequestHeader(InternalHeader.NAME) String token,
                                               @RequestBody ContainerFailureEvent event) {
        if (!InternalHeader.matches(internalToken, token)) {
            log.warn("Rejected container failure notification for container '{}': invalid internal token", event.containerName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Received container failure notification for container '{}' (exitCode={})", event.containerName(), event.exitCode());
        containerFailureNotifier.notifyFailure(event);
        return ResponseEntity.ok().build();
    }
}
