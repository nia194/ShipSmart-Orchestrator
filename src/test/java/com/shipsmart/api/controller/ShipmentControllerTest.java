package com.shipsmart.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.auth.SupabaseJwtVerifier;
import com.shipsmart.api.config.SecurityConfig;
import com.shipsmart.api.domain.ShipmentStatus;
import com.shipsmart.api.dto.CreateShipmentRequest;
import com.shipsmart.api.dto.PatchShipmentRequest;
import com.shipsmart.api.dto.ShipmentSummaryDto;
import com.shipsmart.api.exception.ResourceConflictException;
import com.shipsmart.api.exception.ResourceNotFoundException;
import com.shipsmart.api.repository.IdempotencyKeyRepository;
import com.shipsmart.api.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice test for {@link ShipmentController}.
 *
 * Covers the interview-defensible HTTP contract:
 *   - 401 when unauthenticated
 *   - 201 + Location + ETag on create
 *   - 200 + ETag on get
 *   - 404 ProblemDetail on missing resource
 *   - 400 on validation failure
 *   - 409 ProblemDetail on If-Match version mismatch
 *   - 204 on soft-delete
 */
@WebMvcTest(ShipmentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "shipsmart.supabase.jwt-secret=test-secret-at-least-32-characters-long-for-hmac",
        "shipsmart.security.require-jwt-secret=false"
})
class ShipmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ShipmentService shipmentService;
    @MockitoBean private SupabaseJwtVerifier jwtVerifier;
    @MockitoBean private IdempotencyKeyRepository idempotencyKeyRepository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
    }

    private static ShipmentSummaryDto sampleDto(UUID id, long version) {
        return new ShipmentSummaryDto(
                id, "10001", "90210",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7),
                10.0, 1, ShipmentStatus.DRAFT, version,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"));
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    void get_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void get_authenticated_returns200WithETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(shipmentService.getById(eq(id), eq(USER_ID))).thenReturn(sampleDto(id, 3L));

        mockMvc.perform(get("/api/v1/shipments/{id}", id).with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void get_missing_returns404ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(shipmentService.getById(eq(id), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("Shipment", id.toString()));

        mockMvc.perform(get("/api/v1/shipments/{id}", id).with(authentication(auth())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists());
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Test
    void list_paginated_returnsPage() throws Exception {
        UUID id = UUID.randomUUID();
        Page<ShipmentSummaryDto> page = new PageImpl<>(List.of(sampleDto(id, 0L)));
        when(shipmentService.list(eq(USER_ID), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/shipments")
                        .param("page", "0").param("size", "10")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void create_validBody_returns201WithLocationAndETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(shipmentService.create(any(), eq(USER_ID))).thenReturn(sampleDto(id, 0L));

        CreateShipmentRequest body = new CreateShipmentRequest(
                "10001", "90210",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7),
                List.of(), 10.0, 1);

        mockMvc.perform(post("/api/v1/shipments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/v1/shipments/" + id)))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        String bad = "{\"origin\":\"\",\"destination\":\"90210\",\"dropOffDate\":\"2026-05-01\",\"expectedDeliveryDate\":\"2026-05-07\"}";

        mockMvc.perform(post("/api/v1/shipments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── PATCH ────────────────────────────────────────────────────────────────

    @Test
    void patch_staleIfMatch_returns409ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(shipmentService.updatePartial(eq(id), eq(USER_ID), any(), eq(1L)))
                .thenThrow(new ResourceConflictException("If-Match version 1 does not match current 2"));

        PatchShipmentRequest patch = new PatchShipmentRequest(
                "20001", null, null, null, null, null, null);

        mockMvc.perform(patch("/api/v1/shipments/{id}", id)
                        .with(authentication(auth()))
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void patch_success_returnsUpdatedETag() throws Exception {
        UUID id = UUID.randomUUID();
        when(shipmentService.updatePartial(eq(id), eq(USER_ID), any(), eq(1L)))
                .thenReturn(sampleDto(id, 2L));

        PatchShipmentRequest patch = new PatchShipmentRequest(
                "20001", null, null, null, null, null, null);

        mockMvc.perform(patch("/api/v1/shipments/{id}", id)
                        .with(authentication(auth()))
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void delete_success_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/shipments/{id}", id).with(authentication(auth())))
                .andExpect(status().isNoContent());

        verify(shipmentService).softDelete(id, USER_ID);
    }

    @Test
    void delete_missing_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Shipment", id.toString()))
                .when(shipmentService).softDelete(id, USER_ID);

        mockMvc.perform(delete("/api/v1/shipments/{id}", id).with(authentication(auth())))
                .andExpect(status().isNotFound());
    }
}
