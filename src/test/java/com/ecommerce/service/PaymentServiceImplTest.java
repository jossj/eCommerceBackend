package com.ecommerce.service;

import com.ecommerce.dto.PaymentDTO;
import com.ecommerce.dto.PaymentIntentRequest;
import com.ecommerce.dto.PaymentIntentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.entity.Payment.PaymentStatus;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceAlreadyExistsException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.StripePaymentService;
import com.ecommerce.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StripePaymentService stripePaymentService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order order;
    private Payment payment;
    private PaymentDTO paymentDTO;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L).firstName("John").lastName("Doe")
                .email("john@example.com").password("password").role(User.Role.CUSTOMER)
                .build();

        order = Order.builder()
                .id(1L).user(user)
                .totalAmount(new BigDecimal("999.99"))
                .status(Order.OrderStatus.PENDING)
                .build();

        payment = Payment.builder()
                .id(1L).order(order)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal("999.99"))
                .transactionId("txn-abc-123")
                .currency("AUD")
                .status(PaymentStatus.COMPLETED)
                .build();

        paymentDTO = PaymentDTO.builder()
                .orderId(1L)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("AUD")
                .build();
    }

    @Test
    void processPayment_success() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentDTO result = paymentService.processPayment(paymentDTO);

        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(paymentRepository).save(any(Payment.class));
        verify(orderRepository).save(order);
    }

    @Test
    void processPayment_setsTransactionIdAutomatically() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            assertThat(saved.getTransactionId()).isNotBlank();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            return payment;
        });

        paymentService.processPayment(paymentDTO);
    }

    @Test
    void processPayment_defaultsCurrencyToAud_whenNull() {
        paymentDTO.setCurrency(null);
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            assertThat(saved.getCurrency()).isEqualTo("AUD");
            return payment;
        });

        paymentService.processPayment(paymentDTO);
    }

    @Test
    void processPayment_usesOrderTotalAmount() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("999.99"));
            return payment;
        });

        paymentService.processPayment(paymentDTO);
    }

    @Test
    void processPayment_duplicatePayment_throwsException() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.processPayment(paymentDTO))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("1");

        verify(orderRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_orderNotFound_throwsException() {
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(paymentDTO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void getPaymentById_found() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentDTO result = paymentService.getPaymentById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTransactionId()).isEqualTo("txn-abc-123");
    }

    @Test
    void getPaymentById_notFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getPaymentByOrderId_found() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        PaymentDTO result = paymentService.getPaymentByOrderId(1L);

        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    @Test
    void getPaymentByOrderId_notFound_throwsException() {
        when(paymentRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllPayments_returnsMappedList() {
        when(paymentRepository.findAll()).thenReturn(List.of(payment));

        List<PaymentDTO> result = paymentService.getAllPayments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrency()).isEqualTo("AUD");
    }

    @Test
    void getAllPayments_emptyRepo_returnsEmptyList() {
        when(paymentRepository.findAll()).thenReturn(List.of());

        assertThat(paymentService.getAllPayments()).isEmpty();
    }

    @Test
    void getPaymentsByStatus_returnsFiltered() {
        when(paymentRepository.findByStatus(PaymentStatus.COMPLETED)).thenReturn(List.of(payment));

        List<PaymentDTO> result = paymentService.getPaymentsByStatus(PaymentStatus.COMPLETED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void updatePaymentStatus_success() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.updatePaymentStatus(1L, PaymentStatus.FAILED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void updatePaymentStatus_notFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(99L, PaymentStatus.FAILED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refundPayment_success() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentDTO result = paymentService.refundPayment(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.REFUNDED);
        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
    }

    @Test
    void refundPayment_notFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refundPayment_withStripeIntentId_callsStripeRefund() {
        payment.setStripePaymentIntentId("pi_test_123");
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(stripePaymentService.refundPaymentIntent("pi_test_123", null)).thenReturn(null);

        paymentService.refundPayment(1L);

        verify(stripePaymentService).refundPaymentIntent("pi_test_123", null);
    }

    @Test
    void createPaymentIntent_success() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .orderId(1L).paymentMethod(PaymentMethod.CREDIT_CARD).currency("AUD").build();

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_abc");
        when(mockIntent.getClientSecret()).thenReturn("pi_test_abc_secret_xyz");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");
        when(mockIntent.getAmount()).thenReturn(99999L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stripePaymentService.createPaymentIntent(order.getTotalAmount(), "AUD", 1L, "john@example.com", "John Doe"))
                .thenReturn(mockIntent);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            saved = Payment.builder()
                    .id(1L).order(order)
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .amount(order.getTotalAmount())
                    .currency("AUD")
                    .stripePaymentIntentId("pi_test_abc")
                    .status(PaymentStatus.PENDING)
                    .build();
            return saved;
        });

        PaymentIntentResponse response = paymentService.createPaymentIntent(request);

        assertThat(response.getPaymentIntentId()).isEqualTo("pi_test_abc");
        assertThat(response.getClientSecret()).isEqualTo("pi_test_abc_secret_xyz");
        assertThat(response.getOrderId()).isEqualTo(1L);
    }

    @Test
    void createPaymentIntent_duplicatePayment_throwsException() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .orderId(1L).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        when(paymentRepository.existsByOrderId(1L)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPaymentIntent(request))
                .isInstanceOf(ResourceAlreadyExistsException.class);

        verify(stripePaymentService, never()).createPaymentIntent(any(), any(), any(), any(), any());
    }

    @Test
    void createPaymentIntent_defaultsCurrencyToAud() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .orderId(1L).paymentMethod(PaymentMethod.CREDIT_CARD).build();

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_abc");
        when(mockIntent.getClientSecret()).thenReturn("secret");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");
        when(mockIntent.getAmount()).thenReturn(99999L);
        when(mockIntent.getCurrency()).thenReturn("usd");

        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(stripePaymentService.createPaymentIntent(any(BigDecimal.class), eq("AUD"), eq(Long.valueOf(1L)), any(), any())).thenReturn(mockIntent);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            assertThat(saved.getCurrency()).isEqualTo("AUD");
            return payment;
        });

        paymentService.createPaymentIntent(request);
    }

    @Test
    void cancelStripePayment_success() {
        payment.setStripePaymentIntentId("pi_test_123");
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        PaymentIntent cancelledIntent = mock(PaymentIntent.class);
        when(stripePaymentService.cancelPaymentIntent("pi_test_123")).thenReturn(cancelledIntent);

        PaymentDTO result = paymentService.cancelStripePayment("pi_test_123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(stripePaymentService).cancelPaymentIntent("pi_test_123");
    }

    @Test
    void cancelStripePayment_notFound_throwsException() {
        when(paymentRepository.findByStripePaymentIntentId("pi_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelStripePayment("pi_missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── confirmStripePayment ──────────────────────────────────────────────────

    @Test
    void confirmStripePayment_alreadySucceeded_syncsOrderWithoutReconfirming() {
        payment.setStripePaymentIntentId("pi_test_123");
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));

        PaymentIntent succeededIntent = mock(PaymentIntent.class);
        when(succeededIntent.getStatus()).thenReturn("succeeded");
        when(succeededIntent.getLatestCharge()).thenReturn("ch_test_456");
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(succeededIntent);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        // No paymentMethodId — simulates client-side Stripe.js confirmation
        paymentService.confirmStripePayment("pi_test_123", null);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(stripePaymentService, never()).confirmPaymentIntent(any(), any());
    }

    @Test
    void confirmStripePayment_withPaymentMethodId_alreadySucceeded_syncsWithoutReconfirming() {
        // Even when a paymentMethodId is supplied, if Stripe already shows "succeeded"
        // the service must NOT call confirm() again (it would throw).
        payment.setStripePaymentIntentId("pi_test_123");
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));

        PaymentIntent succeededIntent = mock(PaymentIntent.class);
        when(succeededIntent.getStatus()).thenReturn("succeeded");
        when(succeededIntent.getLatestCharge()).thenReturn("ch_test_456");
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(succeededIntent);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.confirmStripePayment("pi_test_123", "pm_card_visa");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(stripePaymentService, never()).confirmPaymentIntent(any(), any());
    }

    @Test
    void confirmStripePayment_serverSideConfirmation_succeeds() {
        payment.setStripePaymentIntentId("pi_test_123");
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));

        PaymentIntent pendingIntent = mock(PaymentIntent.class);
        when(pendingIntent.getStatus()).thenReturn("requires_payment_method");
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(pendingIntent);

        PaymentIntent confirmedIntent = mock(PaymentIntent.class);
        when(confirmedIntent.getStatus()).thenReturn("succeeded");
        when(confirmedIntent.getLatestCharge()).thenReturn("ch_test_789");
        when(stripePaymentService.confirmPaymentIntent("pi_test_123", "pm_card_visa")).thenReturn(confirmedIntent);

        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.confirmStripePayment("pi_test_123", "pm_card_visa");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(stripePaymentService).confirmPaymentIntent("pi_test_123", "pm_card_visa");
    }

    @Test
    void confirmStripePayment_requiresAction_leavesPaymentPending() {
        payment.setStripePaymentIntentId("pi_test_123");
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));

        PaymentIntent actionIntent = mock(PaymentIntent.class);
        when(actionIntent.getStatus()).thenReturn("requires_action");
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(actionIntent);

        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.confirmStripePayment("pi_test_123", null);

        // 3D Secure in progress — payment stays PENDING; webhook will finalise it
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmStripePayment_notFound_throwsException() {
        when(paymentRepository.findByStripePaymentIntentId("pi_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmStripePayment("pi_missing", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── handleWebhookEvent ────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_paymentIntentSucceeded_updatesPaymentAndOrder() {
        payment.setStripePaymentIntentId("pi_test_123");

        // The event already carries the full PaymentIntent — no extra API call needed.
        PaymentIntent eventIntent = mock(PaymentIntent.class);
        when(eventIntent.getId()).thenReturn("pi_test_123");
        when(eventIntent.getLatestCharge()).thenReturn("ch_test_456");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(eventIntent));

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        when(stripePaymentService.constructWebhookEvent("payload", "sig")).thenReturn(mockEvent);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        paymentService.handleWebhookEvent("payload", "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(payment.getTransactionId()).isEqualTo("ch_test_456");
        // Verify no extra round-trip to Stripe was made
        verify(stripePaymentService, never()).retrievePaymentIntent(any());
    }

    @Test
    void handleWebhookEvent_paymentIntentSucceeded_rawJsonFallback_updatesPaymentAndOrder() throws Exception {
        // Simulates SDK/API-version mismatch where getObject() returns empty.
        payment.setStripePaymentIntentId("pi_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(deserializer.getRawJson()).thenReturn("{\"id\":\"pi_test_123\",\"object\":\"payment_intent\"}");

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        PaymentIntent freshIntent = mock(PaymentIntent.class);
        when(freshIntent.getId()).thenReturn("pi_test_123");
        when(freshIntent.getLatestCharge()).thenReturn("ch_test_456");

        when(stripePaymentService.constructWebhookEvent("payload", "sig")).thenReturn(mockEvent);
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(freshIntent);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        // Use a real ObjectMapper for this test so the raw JSON is actually parsed.
        paymentService = new PaymentServiceImpl(
                paymentRepository, orderRepository, stripePaymentService, new ObjectMapper());

        paymentService.handleWebhookEvent("payload", "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(payment.getTransactionId()).isEqualTo("ch_test_456");
    }

    @Test
    void handleWebhookEvent_paymentIntentSucceeded_retrievePaymentIntentCalledOnlyAsFallback() throws Exception {
        // When deserializer fails (SDK/API version mismatch) the handler must fall back to
        // retrievePaymentIntent to obtain the charge ID — but ONLY in that case.
        payment.setStripePaymentIntentId("pi_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(deserializer.getRawJson()).thenReturn("{\"id\":\"pi_test_123\",\"object\":\"payment_intent\"}");

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.succeeded");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        PaymentIntent freshIntent = mock(PaymentIntent.class);
        when(freshIntent.getId()).thenReturn("pi_test_123");
        when(freshIntent.getLatestCharge()).thenReturn("ch_fallback_456");

        when(stripePaymentService.constructWebhookEvent("payload", "sig")).thenReturn(mockEvent);
        when(stripePaymentService.retrievePaymentIntent("pi_test_123")).thenReturn(freshIntent);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        paymentService = new PaymentServiceImpl(
                paymentRepository, orderRepository, stripePaymentService, new ObjectMapper());

        paymentService.handleWebhookEvent("payload", "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(payment.getTransactionId()).isEqualTo("ch_fallback_456");
        verify(stripePaymentService).retrievePaymentIntent("pi_test_123");
    }

    @Test
    void handleWebhookEvent_paymentIntentFailed_updatesPaymentToFailed() {
        payment.setStripePaymentIntentId("pi_test_123");

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(mockIntent));

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.payment_failed");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        when(stripePaymentService.constructWebhookEvent("payload", "sig")).thenReturn(mockEvent);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.handleWebhookEvent("payload", "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void handleWebhookEvent_paymentIntentCanceled_updatesPaymentToCancelled() {
        payment.setStripePaymentIntentId("pi_test_123");

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_test_123");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(mockIntent));

        Event mockEvent = mock(Event.class);
        when(mockEvent.getType()).thenReturn("payment_intent.canceled");
        when(mockEvent.getDataObjectDeserializer()).thenReturn(deserializer);

        when(stripePaymentService.constructWebhookEvent("payload", "sig")).thenReturn(mockEvent);
        when(paymentRepository.findByStripePaymentIntentId("pi_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.handleWebhookEvent("payload", "sig");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
