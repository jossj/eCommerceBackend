package com.ecommerce.service.impl;

import com.ecommerce.exception.StripePaymentException;
import com.ecommerce.service.StripePaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StripePaymentServiceImpl implements StripePaymentService {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Override
    public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, Long orderId,
                                             String email, String name) {
        try {
            String customerId = getOrCreateCustomer(email, name);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toCents(amount))
                    .setCurrency(currency.toLowerCase())
                    .setCustomer(customerId)
                    .setReceiptEmail(email)
                    .putMetadata("orderId", orderId.toString())
                    .build();
            return PaymentIntent.create(params);
        } catch (StripeException e) {
            throw new StripePaymentException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            throw new StripePaymentException("Failed to retrieve payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                    .setPaymentMethod(paymentMethodId)
                    .build();
            return intent.confirm(params);
        } catch (StripeException e) {
            throw new StripePaymentException("Failed to confirm payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntent cancelPaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return intent.cancel(PaymentIntentCancelParams.builder().build());
        } catch (StripeException e) {
            throw new StripePaymentException("Failed to cancel payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public Refund refundPaymentIntent(String paymentIntentId, BigDecimal amount) {
        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);
            if (amount != null) {
                builder.setAmount(toCents(amount));
            }
            return Refund.create(builder.build());
        } catch (StripeException e) {
            throw new StripePaymentException("Failed to refund payment: " + e.getMessage(), e);
        }
    }

    @Override
    public Event constructWebhookEvent(String payload, String sigHeader) {
        try {
            return Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (StripeException e) {
            throw new StripePaymentException("Invalid webhook signature: " + e.getMessage(), e);
        }
    }

    private String getOrCreateCustomer(String email, String name) throws StripeException {
        var searchResult = Customer.search(
                CustomerSearchParams.builder()
                        .setQuery("email:'" + email + "'")
                        .build());

        if (!searchResult.getData().isEmpty()) {
            return searchResult.getData().get(0).getId();
        }

        return Customer.create(
                CustomerCreateParams.builder()
                        .setEmail(email)
                        .setName(name)
                        .build()
        ).getId();
    }

    private long toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }
}
