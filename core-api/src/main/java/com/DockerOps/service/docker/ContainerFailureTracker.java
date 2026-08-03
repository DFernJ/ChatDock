package com.DockerOps.service.docker;

import com.DockerOps.service.notifications.NotificationService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContainerFailureTracker {

    private static final String ESSENTIAL_CONTAINER_PREFIX = "essential-";

    private record FailureInfo(String containerName, Long exitCode, Instant finishedAt, String diagnosis) {}

    private final Map<String, FailureInfo> failures = new ConcurrentHashMap<>();

    private final Set<String> knownContainerNames = new HashSet<>();
    private boolean lifecycleSeeded = false;

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private ContainerEventPublisher eventPublisher;
    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void pollContainers() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (Container c : containers) {
            seenIds.add(c.getId());
            String name = c.getNames()[0].replace("/", "");
            if (!name.startsWith(ESSENTIAL_CONTAINER_PREFIX)) {
                seenNames.add(name);
            }
            evaluateContainer(c);
        }
        failures.keySet().retainAll(seenIds);
        trackLifecycle(seenNames);
    }

    private void trackLifecycle(Set<String> seenNames) {
        if (!lifecycleSeeded) {
            knownContainerNames.addAll(seenNames);
            lifecycleSeeded = true;
            return;
        }
        for (String name : seenNames) {
            if (knownContainerNames.add(name)) {
                eventPublisher.publishContainerCreated(name);
            }
        }
        for (String name : knownContainerNames) {
            if (!seenNames.contains(name)) {
                eventPublisher.publishContainerDeleted(name);
            }
        }
        knownContainerNames.retainAll(seenNames);
    }

    private void evaluateContainer(Container c) {
        String state = c.getState();
        boolean maybeFailed = "exited".equalsIgnoreCase(state) || "dead".equalsIgnoreCase(state);
        if (!maybeFailed) {
            failures.remove(c.getId());
            return;
        }

        InspectContainerResponse.ContainerState containerState;
        try {
            containerState = dockerClient.inspectContainerCmd(c.getId()).exec().getState();
        } catch (RuntimeException e) {
            return;
        }

        Long exitCode = containerState.getExitCodeLong();
        boolean failed = "dead".equalsIgnoreCase(state) || (exitCode != null && exitCode != 0);
        if (!failed) {
            failures.remove(c.getId());
            return;
        }

        Instant finishedAt = parseInstant(containerState.getFinishedAt());
        FailureInfo existing = failures.get(c.getId());
        if (existing != null && Objects.equals(existing.finishedAt(), finishedAt)) {
            return;
        }

        String name = c.getNames()[0].replace("/", "");
        failures.put(c.getId(), new FailureInfo(name, exitCode, finishedAt, null));
        notificationService.recordContainerFailure(c.getId(), name, exitCode, finishedAt)
                .ifPresent(notification -> eventPublisher.publishContainerFailure(
                        notification.getId(), c.getId(), name, exitCode, finishedAt, notification.getMessage()));
    }

    private Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public boolean hasFailedRecently(String containerId) {
        return failures.containsKey(containerId);
    }

    public Optional<String> getCachedDiagnosis(String containerId) {
        return Optional.ofNullable(failures.get(containerId)).map(FailureInfo::diagnosis);
    }

    public Optional<Instant> getFinishedAt(String containerId) {
        return Optional.ofNullable(failures.get(containerId)).map(FailureInfo::finishedAt);
    }

    public void cacheDiagnosis(String containerId, String diagnosis) {
        failures.computeIfPresent(containerId, (id, info) ->
                new FailureInfo(info.containerName(), info.exitCode(), info.finishedAt(), diagnosis));
    }
}
