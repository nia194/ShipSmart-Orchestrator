package com.shipsmart.api.web;

import com.shipsmart.api.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private final IdempotencyKeyRepository repo;

    public IdempotencyCleanupJob(IdempotencyKeyRepository repo) { this.repo = repo; }

    @Scheduled(fixedRate = 3_600_000L)
    @Transactional
    public void purgeExpired() {
        int removed = repo.deleteExpired(Instant.now());
        if (removed > 0) log.info("Purged {} expired idempotency keys", removed);
    }
}
