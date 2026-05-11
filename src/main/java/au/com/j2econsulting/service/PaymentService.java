package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.PaymentDTO;
import au.com.j2econsulting.dto.PaymentIntentRequest;
import au.com.j2econsulting.dto.PaymentIntentResponse;
import au.com.j2econsulting.entity.Payment.PaymentStatus;

import java.util.List;

public interface PaymentService {

    PaymentDTO processPayment(PaymentDTO paymentDTO);

    PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request);

    PaymentDTO confirmStripePayment(String paymentIntentId, String paymentMethodId);

    PaymentDTO cancelStripePayment(String paymentIntentId);

    void handleWebhookEvent(String payload, String sigHeader);

    PaymentDTO getPaymentById(Long id);

    PaymentDTO getPaymentByOrderId(Long orderId);

    List<PaymentDTO> getAllPayments();

    List<PaymentDTO> getPaymentsByStatus(PaymentStatus status);

    PaymentDTO updatePaymentStatus(Long id, PaymentStatus status);

    PaymentDTO refundPayment(Long id);
}
