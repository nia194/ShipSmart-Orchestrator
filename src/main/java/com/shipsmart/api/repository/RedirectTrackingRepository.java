package com.shipsmart.api.repository;

import com.shipsmart.api.domain.RedirectTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RedirectTrackingRepository extends JpaRepository<RedirectTracking, UUID> {
}
