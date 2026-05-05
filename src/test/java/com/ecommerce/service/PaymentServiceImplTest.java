package com.ecommerce.service;

import com.ecommerce.dto.PaymentDTO;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.entity.Payment.PaymentStatus;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceAlreadyExistsException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.impl.PaymentServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;

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
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .build();

        paymentDTO = PaymentDTO.builder()
                .orderId(1L)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("USD")
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
    void processPayment_defaultsCurrencyToUSD_whenNull() {
        paymentDTO.setCurrency(null);
        when(paymentRepository.existsByOrderId(1L)).thenReturn(false);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            assertThat(saved.getCurrency()).isEqualTo("USD");
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
        assertThat(result.get(0).getCurrency()).isEqualTo("USD");
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
}
