package com.ecommerce.service;

import com.ecommerce.dto.PaymentDTO;
import com.ecommerce.entity.Payment.PaymentStatus;

import java.util.List;

public interface PaymentService {

    PaymentDTO processPayment(PaymentDTO paymentDTO);

    PaymentDTO getPaymentById(Long id);

    PaymentDTO getPaymentByOrderId(Long orderId);

    List<PaymentDTO> getAllPayments();

    List<PaymentDTO> getPaymentsByStatus(PaymentStatus status);

    PaymentDTO updatePaymentStatus(Long id, PaymentStatus status);

    PaymentDTO refundPayment(Long id);
}
