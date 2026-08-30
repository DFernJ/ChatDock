package com.chatops.controller;

import com.chatops.dto.ContainerLifecycleEvent;
import com.chatops.service.ContainerLifecycleNotifier;
import com.chatops.util.InternalHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class ContainerLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(ContainerLifecycleController.class);

    @Value("${security.internal-token}")
    private String internalToken;

    @Autowired
    private InternalHeader internalHeader;

    private final ContainerLifecycleNotifier containerLifecycleNotifier;

    public ContainerLifecycleController(ContainerLifecycleNotifier containerLifecycleNotifier) {
        this.containerLifecycleNotifier = containerLifecycleNotifier;
    }

    @PostMapping("/container-created")
    public ResponseEntity<?> containerCreated(@RequestHeader HttpHeaders headers,
                                               @RequestBody ContainerLifecycleEvent event) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected container-created notification for '{}': invalid internal token", event.containerName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Received container-created notification for '{}'", event.containerName());
        containerLifecycleNotifier.notifyCreated(event);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/container-deleted")
    public ResponseEntity<?> containerDeleted(@RequestHeader HttpHeaders headers,
                                               @RequestBody ContainerLifecycleEvent event) {
        if (!internalHeader.matches(internalToken, headers.getFirst(internalHeader.getName()))) {
            log.warn("Rejected container-deleted notification for '{}': invalid internal token", event.containerName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("Received container-deleted notification for '{}'", event.containerName());
        containerLifecycleNotifier.notifyDeleted(event);
        return ResponseEntity.ok().build();
    }
}
