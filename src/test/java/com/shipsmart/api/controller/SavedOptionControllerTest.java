package com.shipsmart.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.auth.SupabaseJwtVerifier;
import com.shipsmart.api.config.SecurityConfig;
import com.shipsmart.api.dto.SavedOptionResponse;
import com.shipsmart.api.dto.ShippingServiceDto;
import com.shipsmart.api.service.SavedOptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SavedOptionController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "shipsmart.supabase.jwt-secret=test-secret-at-least-32-characters-long-for-hmac",
        "shipsmart.security.require-jwt-secret=false"
})
class SavedOptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SavedOptionService savedOptionService;

    @MockitoBean
    private SupabaseJwtVerifier jwtVerifier;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());
    }

    @Test
    void getSavedOptions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/saved-options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSavedOptions_authenticated_returns200() throws Exception {
        ShippingServiceDto svc = new ShippingServiceDto(
                "ups-ground", "UPS", "UPS Ground", "STANDARD", 58.90, null,
                7, "Wed, Apr 22", null, false, null, null, null, Map.of(), List.of());
        SavedOptionResponse resp = new SavedOptionResponse(
                "id-1", "ups-ground", svc, "NY", "LA", null, null, null, null, "Apr 6, 2026");

        when(savedOptionService.listForUser(USER_ID)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/v1/saved-options")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].svcId").value("ups-ground"));
    }

    @Test
    void saveOption_invalidBody_returns400() throws Exception {
        // Missing required fields
        String body = "{}";

        mockMvc.perform(post("/api/v1/saved-options")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void saveOption_validBody_returns200() throws Exception {
        ShippingServiceDto svc = new ShippingServiceDto(
                "ups-ground", "UPS", "UPS Ground", "STANDARD", 58.90, null,
                7, "Wed, Apr 22", null, false, null, null, null, Map.of(), List.of());
        SavedOptionResponse resp = new SavedOptionResponse(
                "id-1", "ups-ground", svc, "NY", "LA", null, null, null, null, "Apr 6, 2026");

        when(savedOptionService.save(eq(USER_ID), any())).thenReturn(resp);

        Map<String, Object> body = Map.of(
                "quoteServiceId", "ups-ground",
                "carrier", "UPS",
                "serviceName", "UPS Ground",
                "origin", "NY",
                "destination", "LA"
        );

        mockMvc.perform(post("/api/v1/saved-options")
                        .with(authentication(userAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.svcId").value("ups-ground"));
    }

    @Test
    void removeSavedOption_authenticated_returns200() throws Exception {
        when(savedOptionService.remove(USER_ID, "opt-1")).thenReturn(true);

        mockMvc.perform(delete("/api/v1/saved-options/opt-1")
                        .with(authentication(userAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void removeSavedOption_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/saved-options/opt-1"))
                .andExpect(status().isUnauthorized());
    }
}
