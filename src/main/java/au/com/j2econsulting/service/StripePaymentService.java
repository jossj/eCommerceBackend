package au.com.j2econsulting.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;

import java.math.BigDecimal;

public interface StripePaymentService {

    PaymentIntent createPaymentIntent(BigDecimal amount, String currency, Long orderId, String email, String name);

    PaymentIntent retrievePaymentIntent(String paymentIntentId);

    PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId);

    PaymentIntent cancelPaymentIntent(String paymentIntentId);

    Refund refundPaymentIntent(String paymentIntentId, BigDecimal amount);

    Event constructWebhookEvent(String payload, String sigHeader);
}
