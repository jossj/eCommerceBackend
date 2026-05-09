package com.ecommerce.service.impl;

import com.ecommerce.dto.PaymentDTO;
import com.ecommerce.dto.PaymentIntentRequest;
import com.ecommerce.dto.PaymentIntentResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentStatus;
import com.ecommerce.exception.ResourceAlreadyExistsException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.PaymentService;
import com.ecommerce.service.StripePaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StripePaymentService stripePaymentService;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentDTO processPayment(PaymentDTO dto) {
        if (paymentRepository.existsByOrderId(dto.getOrderId())) {
            throw new ResourceAlreadyExistsException("Payment already exists for order id: " + dto.getOrderId());
        }

        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + dto.getOrderId()));

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(dto.getPaymentMethod())
                .amount(order.getTotalAmount())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "AUD")
                .transactionId(UUID.randomUUID().toString())
                .status(PaymentStatus.COMPLETED)
                .build();

        order.setStatus(Order.OrderStatus.CONFIRMED);
        orderRepository.save(order);

        return toDTO(paymentRepository.save(payment));
    }

    @Override
    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new ResourceAlreadyExistsException("Payment already exists for order id: " + request.getOrderId());
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        String currency = request.getCurrency() != null ? request.getCurrency() : "AUD";

        String email = order.getUser().getEmail();
        String name = order.getUser().getFirstName() + " " + order.getUser().getLastName();

        PaymentIntent stripeIntent = stripePaymentService.createPaymentIntent(
                order.getTotalAmount(), currency, order.getId(), email, name);

        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(request.getPaymentMethod())
                .amount(order.getTotalAmount())
                .currency(currency)
                .stripePaymentIntentId(stripeIntent.getId())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        return PaymentIntentResponse.builder()
                .paymentId(payment.getId())
                .orderId(order.getId())
                .paymentIntentId(stripeIntent.getId())
                .clientSecret(stripeIntent.getClientSecret())
                .status(stripeIntent.getStatus())
                .amountInCents(stripeIntent.getAmount())
                .currency(stripeIntent.getCurrency())
                .build();
    }

    @Override
    public PaymentDTO confirmStripePayment(String paymentIntentId, String paymentMethodId) {
        Payment payment = findByIntentOrThrow(paymentIntentId);

        // Always read the current state first. Calling confirm() on an intent that is
        // already in a terminal state (e.g. confirmed client-side via Stripe.js) throws
        // a StripeException and leaves the order stuck in PENDING.
        PaymentIntent intent = stripePaymentService.retrievePaymentIntent(paymentIntentId);

        // Confirmable states are ones where the intent is still waiting for server-side
        // action. Skip calling confirm() for terminal states ("succeeded", "canceled") and
        // for "requires_action" / "processing" where Stripe is handling the next step.
        if (paymentMethodId != null && !paymentMethodId.isBlank()
                && isConfirmable(intent.getStatus())) {
            intent = stripePaymentService.confirmPaymentIntent(paymentIntentId, paymentMethodId);
        }

        if ("succeeded".equals(intent.getStatus())) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(intent.getLatestCharge());
            Order order = payment.getOrder();
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else if (!isInFlight(intent.getStatus())) {
            // Not succeeded and not in a known in-flight state — treat as a failure.
            payment.setStatus(PaymentStatus.FAILED);
        }
        // In-flight states (requires_action, processing, …) are left as-is;
        // the webhook will update them once Stripe finishes processing.

        return toDTO(paymentRepository.save(payment));
    }

    private boolean isConfirmable(String status) {
        return "requires_payment_method".equals(status) || "requires_confirmation".equals(status);
    }

    private boolean isInFlight(String status) {
        return "requires_action".equals(status)
                || "requires_confirmation".equals(status)
                || "requires_payment_method".equals(status)
                || "processing".equals(status);
    }

    @Override
    public PaymentDTO cancelStripePayment(String paymentIntentId) {
        Payment payment = findByIntentOrThrow(paymentIntentId);

        stripePaymentService.cancelPaymentIntent(paymentIntentId);

        payment.setStatus(PaymentStatus.CANCELLED);
        return toDTO(paymentRepository.save(payment));
    }

    @Override
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event = stripePaymentService.constructWebhookEvent(payload, sigHeader);

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                // Use the PaymentIntent embedded in the event directly — no extra API call.
                // Only fall back to retrievePaymentIntent when the SDK cannot deserialise
                // the object (API/SDK version mismatch), because holding a DB transaction
                // open across an external HTTP call risks timeouts and pool exhaustion.
                PaymentIntent intent = resolvePaymentIntentFromEvent(event);
                if (intent == null) return;
                final PaymentIntent resolvedIntent = intent;
                paymentRepository.findByStripePaymentIntentId(resolvedIntent.getId()).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setTransactionId(resolvedIntent.getLatestCharge());
                    paymentRepository.save(payment);
                    Order order = payment.getOrder();
                    order.setStatus(Order.OrderStatus.CONFIRMED);
                    orderRepository.save(order);
                });
            }
            case "payment_intent.payment_failed" -> {
                String intentId = extractPaymentIntentId(event);
                if (intentId == null) return;
                paymentRepository.findByStripePaymentIntentId(intentId).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
            }
            case "payment_intent.canceled" -> {
                String intentId = extractPaymentIntentId(event);
                if (intentId == null) return;
                paymentRepository.findByStripePaymentIntentId(intentId).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(payment);
                });
            }
        }
    }

    /**
     * Returns the PaymentIntent from the webhook event object when the Stripe SDK can
     * deserialise it (happy path — no extra API call needed).  Falls back to fetching
     * from Stripe only when there is a SDK/API version mismatch and the object cannot
     * be deserialised locally.  Returns {@code null} if the intent cannot be resolved.
     */
    private PaymentIntent resolvePaymentIntentFromEvent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        // Fast path: SDK version matches — use the embedded PaymentIntent directly.
        Optional<StripeObject> obj = deserializer.getObject();
        if (obj.isPresent()) {
            return (PaymentIntent) obj.get();
        }

        // Fallback: SDK/API version mismatch — extract the ID from raw JSON and
        // retrieve the intent from Stripe to get accurate latest_charge data.
        try {
            JsonNode node = objectMapper.readTree(deserializer.getRawJson());
            String id = node.path("id").asText(null);
            if (id != null) {
                return stripePaymentService.retrievePaymentIntent(id);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve PaymentIntent from webhook event {}: {}", event.getId(), e.getMessage());
        }
        return null;
    }

    private String extractPaymentIntentId(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        Optional<StripeObject> obj = deserializer.getObject();
        if (obj.isPresent()) {
            return ((PaymentIntent) obj.get()).getId();
        }

        try {
            JsonNode node = objectMapper.readTree(deserializer.getRawJson());
            return node.path("id").asText(null);
        } catch (Exception e) {
            log.warn("Failed to extract PaymentIntent ID from webhook event {}: {}", event.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPaymentById(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order id: " + orderId));
        return toDTO(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getAllPayments() {
        return paymentRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public PaymentDTO updatePaymentStatus(Long id, PaymentStatus status) {
        Payment payment = findOrThrow(id);
        payment.setStatus(status);
        return toDTO(paymentRepository.save(payment));
    }

    @Override
    public PaymentDTO refundPayment(Long id) {
        Payment payment = findOrThrow(id);

        if (payment.getStripePaymentIntentId() != null) {
            stripePaymentService.refundPaymentIntent(payment.getStripePaymentIntentId(), null);
        }

        payment.setStatus(PaymentStatus.REFUNDED);

        Order order = payment.getOrder();
        order.setStatus(Order.OrderStatus.REFUNDED);
        orderRepository.save(order);

        return toDTO(paymentRepository.save(payment));
    }

    private Payment findOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    private Payment findByIntentOrThrow(String paymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for payment intent: " + paymentIntentId));
    }

    private PaymentDTO toDTO(Payment payment) {
        return PaymentDTO.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .currency(payment.getCurrency())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
