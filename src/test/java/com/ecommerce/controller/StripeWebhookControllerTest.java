package com.ecommerce.controller;

import com.ecommerce.exception.StripePaymentException;
import com.ecommerce.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
class StripeWebhookControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;

    private static final String SAMPLE_PAYLOAD = "{\"type\":\"payment_intent.succeeded\"}";
    private static final String SAMPLE_SIG = "t=1234,v1=abc123";

    @Test
    void handleWebhook_validSignature_returns200() throws Exception {
        doNothing().when(paymentService).handleWebhookEvent(anyString(), anyString());

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SAMPLE_SIG)
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhookEvent(SAMPLE_PAYLOAD, SAMPLE_SIG);
    }

    @Test
    void handleWebhook_invalidSignature_returns402() throws Exception {
        doThrow(new StripePaymentException("Invalid webhook signature"))
                .when(paymentService).handleWebhookEvent(anyString(), anyString());

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "invalid_sig")
                        .content(SAMPLE_PAYLOAD))
                .andExpect(status().isPaymentRequired());
    }
}
