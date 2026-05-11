package au.com.j2econsulting.controller;

import au.com.j2econsulting.dto.PaymentDTO;
import au.com.j2econsulting.dto.PaymentIntentRequest;
import au.com.j2econsulting.dto.PaymentIntentResponse;
import au.com.j2econsulting.entity.Payment.PaymentStatus;
import au.com.j2econsulting.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentDTO> processPayment(@Valid @RequestBody PaymentDTO paymentDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(paymentDTO));
    }

    @PostMapping("/intents")
    public ResponseEntity<PaymentIntentResponse> createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPaymentIntent(request));
    }

    @PostMapping("/intents/{intentId}/confirm")
    public ResponseEntity<PaymentDTO> confirmPaymentIntent(
            @PathVariable String intentId,
            @RequestBody(required = false) Map<String, String> body) {
        String paymentMethodId = body != null ? body.get("paymentMethodId") : null;
        return ResponseEntity.ok(paymentService.confirmStripePayment(intentId, paymentMethodId));
    }

    @PostMapping("/intents/{intentId}/cancel")
    public ResponseEntity<PaymentDTO> cancelPaymentIntent(@PathVariable String intentId) {
        return ResponseEntity.ok(paymentService.cancelStripePayment(intentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentDTO> getPaymentByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentDTO>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<PaymentDTO>> getPaymentsByStatus(@PathVariable PaymentStatus status) {
        return ResponseEntity.ok(paymentService.getPaymentsByStatus(status));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PaymentDTO> updateStatus(@PathVariable Long id,
                                                   @RequestParam PaymentStatus status) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(id, status));
    }

    @PatchMapping("/{id}/refund")
    public ResponseEntity<PaymentDTO> refundPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
