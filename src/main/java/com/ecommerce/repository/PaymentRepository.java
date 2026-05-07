package com.ecommerce.repository;

import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
}
