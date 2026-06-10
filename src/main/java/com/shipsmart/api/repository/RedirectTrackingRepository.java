package com.shipsmart.api.repository;

import com.shipsmart.api.domain.RedirectTracking;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RedirectTrackingRepository extends JpaRepository<RedirectTracking, UUID> {}
