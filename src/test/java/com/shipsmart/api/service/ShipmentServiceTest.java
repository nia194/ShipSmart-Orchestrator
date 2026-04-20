package com.shipsmart.api.service;

import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import com.shipsmart.api.dto.CreateShipmentRequest;
import com.shipsmart.api.dto.PatchShipmentRequest;
import com.shipsmart.api.exception.ResourceConflictException;
import com.shipsmart.api.exception.ResourceNotFoundException;
import com.shipsmart.api.repository.ShipmentRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipmentServiceTest {

    private ShipmentRequestRepository repo;
    private ShipmentService service;

    @BeforeEach
    void setUp() {
        repo = mock(ShipmentRequestRepository.class);
        service = new ShipmentService(repo);
    }

    @Test
    void createPersistsAndReturnsDto() {
        CreateShipmentRequest req = new CreateShipmentRequest(
                "NYC", "LON", LocalDate.now(), LocalDate.now().plusDays(5),
                null, 10.0, 2);
        when(repo.save(any(ShipmentRequest.class))).thenAnswer(inv -> {
            ShipmentRequest s = inv.getArgument(0);
            return s;
        });
        var dto = service.create(req, "user-123");
        ArgumentCaptor<ShipmentRequest> cap = ArgumentCaptor.forClass(ShipmentRequest.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ShipmentStatus.DRAFT);
        assertThat(cap.getValue().getUserId()).isEqualTo("user-123");
        assertThat(dto.origin()).isEqualTo("NYC");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, "u")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id, "u"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePartialWithStaleVersionThrowsConflict() {
        UUID id = UUID.randomUUID();
        ShipmentRequest s = new ShipmentRequest();
        s.setUserId("u");
        s.setVersion(5L);
        when(repo.findByIdAndUserId(id, "u")).thenReturn(Optional.of(s));
        PatchShipmentRequest patch = new PatchShipmentRequest("X", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updatePartial(id, "u", patch, 4L))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void softDeleteSetsDeletedAt() {
        UUID id = UUID.randomUUID();
        ShipmentRequest s = new ShipmentRequest();
        s.setUserId("u");
        when(repo.findByIdAndUserId(id, "u")).thenReturn(Optional.of(s));
        service.softDelete(id, "u");
        assertThat(s.getDeletedAt()).isNotNull();
    }
}
