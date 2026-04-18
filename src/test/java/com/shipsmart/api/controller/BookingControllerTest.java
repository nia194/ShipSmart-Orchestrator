package com.shipsmart.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.auth.SupabaseJwtVerifier;
import com.shipsmart.api.config.SecurityConfig;
import com.shipsmart.api.dto.BookingRedirectResponse;
import com.shipsmart.api.repository.IdempotencyKeyRepository;
import com.shipsmart.api.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "shipsmart.supabase.jwt-secret=test-secret-at-least-32-characters-long-for-hmac",
        "shipsmart.security.require-jwt-secret=false"
})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private SupabaseJwtVerifier jwtVerifier;

    @MockitoBean
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void trackRedirect_unauthenticated_returns200() throws Exception {
        when(bookingService.trackAndRedirect(any(), any()))
                .thenReturn(new BookingRedirectResponse("https://ups.com/checkout"));

        Map<String, String> body = Map.of(
                "serviceId", "ups-ground",
                "redirectUrl", "https://ups.com/checkout"
        );

        mockMvc.perform(post("/api/v1/bookings/redirect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("https://ups.com/checkout"));
    }

    @Test
    void trackRedirect_authenticated_returns200() throws Exception {
        when(bookingService.trackAndRedirect(any(), any()))
                .thenReturn(new BookingRedirectResponse("https://fedex.com/ship"));

        Map<String, String> body = Map.of(
                "serviceId", "fedex-express",
                "redirectUrl", "https://fedex.com/ship",
                "carrier", "FedEx",
                "serviceName", "FedEx Express"
        );

        var auth = new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList());

        mockMvc.perform(post("/api/v1/bookings/redirect")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("https://fedex.com/ship"));
    }

    @Test
    void trackRedirect_invalidBody_returns400() throws Exception {
        // Missing required serviceId and redirectUrl
        String body = "{}";

        mockMvc.perform(post("/api/v1/bookings/redirect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
