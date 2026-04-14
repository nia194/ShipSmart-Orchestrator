package com.shipsmart.api.service;

import com.shipsmart.api.domain.RedirectTracking;
import com.shipsmart.api.dto.BookingRedirectRequest;
import com.shipsmart.api.dto.BookingRedirectResponse;
import com.shipsmart.api.repository.RedirectTrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private RedirectTrackingRepository repository;

    private BookingService service;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        service = new BookingService(repository);
    }

    @Test
    void trackAndRedirect_returnsRedirectUrl() {
        BookingRedirectRequest request = new BookingRedirectRequest(
                "ups-ground", "UPS", "UPS® Ground",
                "https://ups.com/checkout/abc", "New York, NY", "Los Angeles, CA"
        );
        when(repository.save(any(RedirectTracking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingRedirectResponse response = service.trackAndRedirect(Optional.of(USER_ID), request);

        assertEquals("https://ups.com/checkout/abc", response.redirectUrl());
    }

    @Test
    void trackAndRedirect_persistsEntityWithUserId() {
        BookingRedirectRequest request = new BookingRedirectRequest(
                "fedex-express", "FedEx", "FedEx Express",
                "https://fedex.com/ship", "Chicago, IL", "Miami, FL"
        );
        when(repository.save(any(RedirectTracking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.trackAndRedirect(Optional.of(USER_ID), request);

        ArgumentCaptor<RedirectTracking> captor = ArgumentCaptor.forClass(RedirectTracking.class);
        verify(repository).save(captor.capture());
        RedirectTracking saved = captor.getValue();
        assertEquals(UUID.fromString(USER_ID), saved.getUserId());
        assertEquals("fedex-express", saved.getServiceId());
        assertEquals("FedEx", saved.getCarrier());
        assertEquals("FedEx Express", saved.getServiceName());
        assertEquals("https://fedex.com/ship", saved.getRedirectUrl());
        assertEquals("Chicago, IL", saved.getOrigin());
        assertEquals("Miami, FL", saved.getDestination());
    }

    @Test
    void trackAndRedirect_worksWithoutUserId() {
        BookingRedirectRequest request = new BookingRedirectRequest(
                "usps-priority", "USPS", "Priority Mail",
                "https://usps.com/ship", null, null
        );
        when(repository.save(any(RedirectTracking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingRedirectResponse response = service.trackAndRedirect(Optional.empty(), request);

        assertEquals("https://usps.com/ship", response.redirectUrl());

        ArgumentCaptor<RedirectTracking> captor = ArgumentCaptor.forClass(RedirectTracking.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    void trackAndRedirect_defaultsNullCarrierAndServiceName() {
        BookingRedirectRequest request = new BookingRedirectRequest(
                "svc-1", null, null,
                "https://example.com", null, null
        );
        when(repository.save(any(RedirectTracking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.trackAndRedirect(Optional.empty(), request);

        ArgumentCaptor<RedirectTracking> captor = ArgumentCaptor.forClass(RedirectTracking.class);
        verify(repository).save(captor.capture());
        assertEquals("", captor.getValue().getCarrier());
        assertEquals("", captor.getValue().getServiceName());
    }
}
