package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentResponse {

    private Long paymentId;
    private Long orderId;
    private String paymentIntentId;
    private String clientSecret;
    private String status;
    private Long amountInCents;
    private String currency;
}
