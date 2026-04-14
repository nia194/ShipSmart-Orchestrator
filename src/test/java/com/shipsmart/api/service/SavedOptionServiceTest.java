package com.shipsmart.api.service;

import com.shipsmart.api.domain.SavedOption;
import com.shipsmart.api.dto.SaveOptionRequest;
import com.shipsmart.api.dto.SavedOptionResponse;
import com.shipsmart.api.repository.SavedOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedOptionServiceTest {

    @Mock
    private SavedOptionRepository repository;

    private SavedOptionService service;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER_ID = "22222222-2222-2222-2222-222222222222";

    @BeforeEach
    void setUp() {
        service = new SavedOptionService(repository);
    }

    @Test
    void save_persistsAndReturnsCorrectResponse() {
        SaveOptionRequest request = new SaveOptionRequest(
                "ups-ground", "UPS", "UPS® Ground",
                "New York, NY", "Los Angeles, CA",
                "STANDARD", 58.90, null, 7,
                "Wed, Apr 22", null, false,
                null, "Best value.", null, null,
                List.of("Tracking"),
                "2026-04-15", "2026-04-22",
                "1x Luggage (25 lbs)", "https://ups.com"
        );

        SavedOption savedEntity = buildEntity(USER_ID, "ups-ground", "UPS", "UPS® Ground");
        when(repository.save(any(SavedOption.class))).thenReturn(savedEntity);

        SavedOptionResponse response = service.save(USER_ID, request);

        assertNotNull(response);
        assertEquals("ups-ground", response.svcId());
        assertEquals("New York, NY", response.origin());
        assertEquals("Los Angeles, CA", response.dest());
        assertNotNull(response.svc());
        assertEquals("UPS", response.svc().carrier());
        assertEquals("UPS® Ground", response.svc().name());

        // Verify entity was saved with correct userId
        ArgumentCaptor<SavedOption> captor = ArgumentCaptor.forClass(SavedOption.class);
        verify(repository).save(captor.capture());
        assertEquals(UUID.fromString(USER_ID), captor.getValue().getUserId());
    }

    @Test
    void save_appliesDefaults() {
        SaveOptionRequest request = new SaveOptionRequest(
                "test-svc", "TestCarrier", "Test Service",
                "A", "B",
                null, null, null, null,  // tier, price, originalPrice, transitDays all null
                null, null, null,
                null, null, null, null,
                null, null, null, null, null
        );

        SavedOption entity = buildEntity(USER_ID, "test-svc", "TestCarrier", "Test Service");
        when(repository.save(any())).thenReturn(entity);

        service.save(USER_ID, request);

        ArgumentCaptor<SavedOption> captor = ArgumentCaptor.forClass(SavedOption.class);
        verify(repository).save(captor.capture());
        SavedOption saved = captor.getValue();
        assertEquals("STANDARD", saved.getTier());
        assertEquals(0, saved.getPrice().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getTransitDays());
        assertFalse(saved.isGuaranteed());
        assertArrayEquals(new String[0], saved.getFeatures());
    }

    @Test
    void listForUser_returnsMappedResponses() {
        UUID uid = UUID.fromString(USER_ID);
        List<SavedOption> entities = List.of(
                buildEntity(USER_ID, "ups-ground", "UPS", "UPS® Ground"),
                buildEntity(USER_ID, "fedex-express", "FedEx", "FedEx Express Saver®")
        );
        when(repository.findByUserIdOrderByCreatedAtDesc(uid)).thenReturn(entities);

        List<SavedOptionResponse> results = service.listForUser(USER_ID);

        assertEquals(2, results.size());
        assertEquals("ups-ground", results.get(0).svcId());
        assertEquals("fedex-express", results.get(1).svcId());
    }

    @Test
    void listForUser_returnsEmptyWhenNoneExist() {
        UUID uid = UUID.fromString(USER_ID);
        when(repository.findByUserIdOrderByCreatedAtDesc(uid)).thenReturn(List.of());

        List<SavedOptionResponse> results = service.listForUser(USER_ID);

        assertTrue(results.isEmpty());
    }

    @Test
    void remove_deletesWhenOwned() {
        UUID uid = UUID.fromString(USER_ID);
        UUID optionId = UUID.randomUUID();
        SavedOption entity = buildEntity(USER_ID, "ups-ground", "UPS", "UPS® Ground");
        when(repository.findByIdAndUserId(optionId, uid)).thenReturn(Optional.of(entity));

        boolean result = service.remove(USER_ID, optionId.toString());

        assertTrue(result);
        verify(repository).delete(entity);
    }

    @Test
    void remove_returnsFalseWhenNotOwned() {
        UUID otherUid = UUID.fromString(OTHER_USER_ID);
        UUID optionId = UUID.randomUUID();
        when(repository.findByIdAndUserId(optionId, otherUid)).thenReturn(Optional.empty());

        boolean result = service.remove(OTHER_USER_ID, optionId.toString());

        assertFalse(result);
        verify(repository, never()).delete(any());
    }

    @Test
    void remove_returnsFalseWhenNotFound() {
        UUID uid = UUID.fromString(USER_ID);
        UUID optionId = UUID.randomUUID();
        when(repository.findByIdAndUserId(optionId, uid)).thenReturn(Optional.empty());

        boolean result = service.remove(USER_ID, optionId.toString());

        assertFalse(result);
        verify(repository, never()).delete(any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SavedOption buildEntity(String userId, String svcId, String carrier, String serviceName) {
        SavedOption e = new SavedOption();
        // Use reflection or setter to set the id since it's generated
        try {
            var idField = SavedOption.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, UUID.randomUUID());
            var createdAtField = SavedOption.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(e, Instant.now());
        } catch (Exception ex) {
            // ignore — tests still work
        }
        e.setUserId(UUID.fromString(userId));
        e.setQuoteServiceId(svcId);
        e.setCarrier(carrier);
        e.setServiceName(serviceName);
        e.setTier("STANDARD");
        e.setPrice(BigDecimal.valueOf(58.90));
        e.setTransitDays(7);
        e.setEstimatedDelivery("Wed, Apr 22");
        e.setGuaranteed(false);
        e.setFeatures(new String[]{"Tracking"});
        e.setOrigin("New York, NY");
        e.setDestination("Los Angeles, CA");
        e.setDropOffDate("2026-04-15");
        e.setExpectedDeliveryDate("2026-04-22");
        e.setPackageSummary("1x Luggage (25 lbs)");
        e.setBookUrl("https://ups.com");
        return e;
    }
}
