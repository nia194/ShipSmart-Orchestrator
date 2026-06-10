package com.shipsmart.api.repository;

import com.shipsmart.api.domain.IdempotencyKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :cutoff")
    int deleteExpired(Instant cutoff);
}
