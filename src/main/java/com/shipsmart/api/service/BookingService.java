package com.shipsmart.api.service;

import com.shipsmart.api.domain.RedirectTracking;
import com.shipsmart.api.dto.BookingRedirectRequest;
import com.shipsmart.api.dto.BookingRedirectResponse;
import com.shipsmart.api.repository.RedirectTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles booking redirect tracking.
 * Replaces the legacy Supabase edge function: generate-book-redirect.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final RedirectTrackingRepository repository;

    public BookingService(RedirectTrackingRepository repository) {
        this.repository = repository;
    }

    /**
     * Persist a redirect tracking record and return the redirect URL.
     *
     * @param userId  optional authenticated user ID
     * @param request booking redirect payload
     * @return response containing the redirect URL
     */
    public BookingRedirectResponse trackAndRedirect(Optional<String> userId, BookingRedirectRequest request) {
        RedirectTracking entity = new RedirectTracking();
        userId.ifPresent(uid -> entity.setUserId(UUID.fromString(uid)));
        entity.setServiceId(request.serviceId());
        entity.setCarrier(request.carrier() != null ? request.carrier() : "");
        entity.setServiceName(request.serviceName() != null ? request.serviceName() : "");
        entity.setRedirectUrl(request.redirectUrl());
        entity.setOrigin(request.origin());
        entity.setDestination(request.destination());

        repository.save(entity);
        log.debug("Tracked booking redirect for service {} (user={})", request.serviceId(), userId.orElse("anonymous"));

        return new BookingRedirectResponse(request.redirectUrl());
    }
}
