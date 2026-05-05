package com.ecommerce.dto;

import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.entity.Payment.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDTO {

    private Long id;

    @NotNull(message = "Order ID is required")
    private Long orderId;

    private PaymentStatus status;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private BigDecimal amount;

    private String transactionId;

    private String currency;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
