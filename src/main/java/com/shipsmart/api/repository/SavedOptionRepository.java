package com.shipsmart.api.repository;

import com.shipsmart.api.domain.SavedOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@code saved_options}.
 * All queries are scoped by userId to enforce data isolation.
 */
public interface SavedOptionRepository extends JpaRepository<SavedOption, UUID> {

    /** Fetch all saved options for a user, newest first. */
    List<SavedOption> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Fetch all saved options for a user, unsorted — analytics sorts/groups in memory. */
    List<SavedOption> findByUserId(UUID userId);

    /** Find a specific saved option owned by a user (for delete authorization). */
    Optional<SavedOption> findByIdAndUserId(UUID id, UUID userId);
}
