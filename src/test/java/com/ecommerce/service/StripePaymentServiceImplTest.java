package com.ecommerce.service;

import com.ecommerce.exception.StripePaymentException;
import com.ecommerce.service.impl.StripePaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StripePaymentServiceImpl.
 *
 * Full integration tests require valid Stripe test API keys configured via
 * STRIPE_API_KEY and STRIPE_WEBHOOK_SECRET environment variables.
 * These tests verify the service structure and error handling using placeholder credentials.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentServiceImplTest {

    @InjectMocks
    private StripePaymentServiceImpl stripePaymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stripePaymentService, "webhookSecret", "whsec_test_placeholder");
    }

    @Test
    void createPaymentIntent_withInvalidApiKey_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.createPaymentIntent(new BigDecimal("99.99"), "AUD", 1L, "test@example.com", "Test User"))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Failed to create payment intent");
    }

    @Test
    void retrievePaymentIntent_withInvalidId_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.retrievePaymentIntent("pi_invalid_id"))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Failed to retrieve payment intent");
    }

    @Test
    void confirmPaymentIntent_withInvalidId_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.confirmPaymentIntent("pi_invalid_id", "pm_card_visa"))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Failed to confirm payment intent");
    }

    @Test
    void cancelPaymentIntent_withInvalidId_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.cancelPaymentIntent("pi_invalid_id"))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Failed to cancel payment intent");
    }

    @Test
    void refundPaymentIntent_withInvalidId_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.refundPaymentIntent("pi_invalid_id", new BigDecimal("50.00")))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Failed to refund payment");
    }

    @Test
    void constructWebhookEvent_withInvalidSignature_throwsStripePaymentException() {
        assertThatThrownBy(() ->
                stripePaymentService.constructWebhookEvent(
                        "{\"type\":\"payment_intent.succeeded\"}", "invalid_signature"))
                .isInstanceOf(StripePaymentException.class)
                .hasMessageContaining("Invalid webhook signature");
    }
}
