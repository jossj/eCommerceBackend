package com.ecommerce.integration;

import com.ecommerce.config.TestSecurityConfig;
import com.ecommerce.dto.*;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.repository.*;
import com.ecommerce.service.StripePaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests that verify the order status is actually committed to the
 * database (not just visible within the same transaction).
 *
 * These tests deliberately do NOT use @Transactional/@Rollback so that each
 * HTTP request commits independently, exactly as it would in production.
 * Cleanup is handled in @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class StripePaymentCommitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private StripePaymentService stripePaymentService;

    private Long orderId;
    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        String userResp = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Stripe", "lastName", "Tester",
                                "email", "stripetest@example.com", "password", "password"))))
                .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readValue(userResp, UserDTO.class).getId();

        ProductDTO product = ProductDTO.builder()
                .name("Test Product").price(new BigDecimal("100.00"))
                .stockQuantity(10).active(true).build();
        String productResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readValue(productResp, ProductDTO.class).getId();

        OrderDTO order = OrderDTO.builder()
                .userId(userId).shippingAddress("1 Test St")
                .orderItems(List.of(OrderItemDTO.builder()
                        .productId(productId).quantity(1).build()))
                .build();
        String orderResp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andReturn().getResponse().getContentAsString();
        orderId = objectMapper.readValue(orderResp, OrderDTO.class).getId();
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── confirmStripePayment ──────────────────────────────────────────────────

    @Test
    void confirmStripePayment_clientSideConfirmed_commitsOrderConfirmedToDatabase() throws Exception {
        // Step 1: create payment intent (mocking Stripe)
        PaymentIntent mockCreatedIntent = mock(PaymentIntent.class);
        when(mockCreatedIntent.getId()).thenReturn("pi_test_commit_001");
        when(mockCreatedIntent.getClientSecret()).thenReturn("pi_test_commit_001_secret");
        when(mockCreatedIntent.getStatus()).thenReturn("requires_payment_method");
        when(mockCreatedIntent.getAmount()).thenReturn(10000L);
        when(mockCreatedIntent.getCurrency()).thenReturn("aud");
        when(stripePaymentService.createPaymentIntent(any(), any(), any(), any(), any()))
                .thenReturn(mockCreatedIntent);

        PaymentIntentRequest intentRequest = PaymentIntentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("AUD")
                .build();

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentRequest)))
                .andExpect(status().isCreated());

        // Verify payment saved with PENDING status — separate transaction
        Payment savedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(savedPayment.getStripePaymentIntentId()).isEqualTo("pi_test_commit_001");
        assertThat(savedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);

        // Verify order still PENDING at this point
        Order orderBeforeConfirm = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderBeforeConfirm.getStatus()).isEqualTo(Order.OrderStatus.PENDING);

        // Step 2: client confirms via Stripe.js (simulated) — mock retrieve returns "succeeded"
        PaymentIntent succeededIntent = mock(PaymentIntent.class);
        when(succeededIntent.getStatus()).thenReturn("succeeded");
        when(succeededIntent.getLatestCharge()).thenReturn("ch_test_commit_001");
        when(stripePaymentService.retrievePaymentIntent("pi_test_commit_001"))
                .thenReturn(succeededIntent);

        // Step 3: client calls /confirm to sync server state (no paymentMethodId — client already confirmed)
        mockMvc.perform(post("/api/payments/intents/pi_test_commit_001/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Step 4: read order in a FRESH transaction — proves the DB was actually committed
        Order orderAfterConfirm = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfterConfirm.getStatus())
                .as("Order must be CONFIRMED in the database after confirmStripePayment commits")
                .isEqualTo(Order.OrderStatus.CONFIRMED);

        // Verify payment committed too
        Payment confirmedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(confirmedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(confirmedPayment.getTransactionId()).isEqualTo("ch_test_commit_001");
    }

    @Test
    void handleWebhookEvent_paymentIntentSucceeded_commitsOrderConfirmedToDatabase() throws Exception {
        // Step 1: create payment intent and save payment
        PaymentIntent mockCreatedIntent = mock(PaymentIntent.class);
        when(mockCreatedIntent.getId()).thenReturn("pi_test_webhook_001");
        when(mockCreatedIntent.getClientSecret()).thenReturn("pi_test_webhook_001_secret");
        when(mockCreatedIntent.getStatus()).thenReturn("requires_payment_method");
        when(mockCreatedIntent.getAmount()).thenReturn(10000L);
        when(mockCreatedIntent.getCurrency()).thenReturn("aud");
        when(stripePaymentService.createPaymentIntent(any(), any(), any(), any(), any()))
                .thenReturn(mockCreatedIntent);

        PaymentIntentRequest intentRequest = PaymentIntentRequest.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("AUD")
                .build();

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(intentRequest)))
                .andExpect(status().isCreated());

        // Step 2: simulate Stripe webhook — mock constructWebhookEvent and retrievePaymentIntent
        PaymentIntent eventIntent = mock(PaymentIntent.class);
        when(eventIntent.getId()).thenReturn("pi_test_webhook_001");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(eventIntent));

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        when(stripePaymentService.constructWebhookEvent(anyString(), anyString()))
                .thenReturn(mockEvent);

        PaymentIntent freshIntent = mock(PaymentIntent.class);
        when(freshIntent.getLatestCharge()).thenReturn("ch_test_webhook_001");
        when(stripePaymentService.retrievePaymentIntent("pi_test_webhook_001"))
                .thenReturn(freshIntent);

        // Step 3: deliver webhook
        String webhookPayload = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_test_webhook_001\"}}}";
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1234,v1=fake")
                        .content(webhookPayload))
                .andExpect(status().isOk());

        // Step 4: read order in a FRESH transaction — proves the DB was actually committed
        Order orderAfterWebhook = orderRepository.findById(orderId).orElseThrow();
        assertThat(orderAfterWebhook.getStatus())
                .as("Order must be CONFIRMED in the database after webhook handler commits")
                .isEqualTo(Order.OrderStatus.CONFIRMED);

        Payment confirmedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(confirmedPayment.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(confirmedPayment.getTransactionId()).isEqualTo("ch_test_webhook_001");
    }
}
