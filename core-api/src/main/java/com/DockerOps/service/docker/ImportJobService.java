package com.DockerOps.service.docker;

import com.DockerOps.dto.response.ImportJobDTO;
import com.DockerOps.exception.NotFoundException;
import com.DockerOps.model.users.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Runs long import operations (image builds) in the background so the HTTP request returns immediately
 * and doesn't hit proxy timeouts. State lives in memory: it is lost if core-api restarts.
 */
@Slf4j
@Service
public class ImportJobService {

    private static final Duration FINISHED_JOB_TTL = Duration.ofMinutes(30);

    private static class Job {
        final String id = UUID.randomUUID().toString();
        final String key;
        final UUID ownerId;
        volatile String status = "RUNNING";
        volatile Object result;
        volatile String error;
        volatile Instant finishedAt;

        Job(String key, UUID ownerId) {
            this.key = key;
            this.ownerId = ownerId;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** @param key identifies the thing being built (e.g. the upload id); only one running job per key is allowed. */
    public String submit(String key, User owner, Supplier<Object> work) {
        purgeExpired();
        Job job = new Job(key, owner.getId());
        synchronized (this) {
            boolean alreadyRunning = jobs.values().stream()
                    .anyMatch(j -> j.key.equals(key) && "RUNNING".equals(j.status));
            if (alreadyRunning) {
                throw new IllegalArgumentException("This import is already being processed");
            }
            jobs.put(job.id, job);
        }
        executor.submit(() -> {
            try {
                job.result = work.get();
                job.status = "DONE";
            } catch (Throwable t) {
                log.error("[job {}] failed", job.id, t);
                job.error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                job.status = "FAILED";
            } finally {
                job.finishedAt = Instant.now();
            }
        });
        return job.id;
    }

    public ImportJobDTO get(String jobId, User requester) {
        Job job = jobs.get(jobId);
        if (job == null || !job.ownerId.equals(requester.getId())) {
            throw new NotFoundException("Job not found");
        }
        return new ImportJobDTO(job.id, job.status, job.result, job.error);
    }

    private void purgeExpired() {
        Instant limit = Instant.now().minus(FINISHED_JOB_TTL);
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(limit));
    }
}
