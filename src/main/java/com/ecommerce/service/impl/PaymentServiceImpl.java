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
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StripePaymentService stripePaymentService;

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

        // If a payment method is supplied the intent still needs server-side confirmation.
        // Otherwise the client (e.g. Stripe.js) already confirmed it — just sync the status.
        PaymentIntent intent = (paymentMethodId != null && !paymentMethodId.isBlank())
                ? stripePaymentService.confirmPaymentIntent(paymentIntentId, paymentMethodId)
                : stripePaymentService.retrievePaymentIntent(paymentIntentId);

        if ("succeeded".equals(intent.getStatus())) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(intent.getLatestCharge());
            Order order = payment.getOrder();
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else if (!"requires_action".equals(intent.getStatus())
                && !"requires_confirmation".equals(intent.getStatus())) {
            // requires_action / requires_confirmation mean the payment is still in-flight
            // (e.g. 3D Secure); leave it PENDING so the webhook can finalise it.
            payment.setStatus(PaymentStatus.FAILED);
        }

        return toDTO(paymentRepository.save(payment));
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

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (!deserializer.getObject().isPresent()) {
            return;
        }

        StripeObject stripeObject = deserializer.getObject().get();

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent intent = (PaymentIntent) stripeObject;
                paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setTransactionId(intent.getLatestCharge());
                    paymentRepository.save(payment);
                    Order order = payment.getOrder();
                    order.setStatus(Order.OrderStatus.CONFIRMED);
                    orderRepository.save(order);
                });
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = (PaymentIntent) stripeObject;
                paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                });
            }
            case "payment_intent.canceled" -> {
                PaymentIntent intent = (PaymentIntent) stripeObject;
                paymentRepository.findByStripePaymentIntentId(intent.getId()).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(payment);
                });
            }
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
