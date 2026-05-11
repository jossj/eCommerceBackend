package au.com.j2econsulting.repository;

import au.com.j2econsulting.entity.Payment;
import au.com.j2econsulting.entity.Payment.PaymentStatus;
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
