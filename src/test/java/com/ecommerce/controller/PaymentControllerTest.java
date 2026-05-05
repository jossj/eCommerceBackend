package com.ecommerce.controller;

import com.ecommerce.dto.PaymentDTO;
import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.entity.Payment.PaymentStatus;
import com.ecommerce.exception.ResourceAlreadyExistsException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PaymentService paymentService;

    private PaymentDTO paymentDTO;

    @BeforeEach
    void setUp() {
        paymentDTO = PaymentDTO.builder()
                .id(1L)
                .orderId(1L)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(new BigDecimal("999.99"))
                .transactionId("txn-abc-123")
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .build();
    }

    @Test
    void processPayment_validInput_returns201() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(1L).paymentMethod(PaymentMethod.CREDIT_CARD).currency("USD").build();
        when(paymentService.processPayment(any(PaymentDTO.class))).thenReturn(paymentDTO);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionId").value("txn-abc-123"));
    }

    @Test
    void processPayment_missingOrderId_returns400() throws Exception {
        PaymentDTO invalid = PaymentDTO.builder().paymentMethod(PaymentMethod.PAYPAL).build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processPayment_missingPaymentMethod_returns400() throws Exception {
        PaymentDTO invalid = PaymentDTO.builder().orderId(1L).build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processPayment_duplicatePayment_returns409() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(1L).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        when(paymentService.processPayment(any(PaymentDTO.class)))
                .thenThrow(new ResourceAlreadyExistsException("Payment already exists for order id: 1"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void processPayment_orderNotFound_returns404() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(99L).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        when(paymentService.processPayment(any(PaymentDTO.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentById_found_returns200() throws Exception {
        when(paymentService.getPaymentById(1L)).thenReturn(paymentDTO);

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getPaymentById_notFound_returns404() throws Exception {
        when(paymentService.getPaymentById(99L))
                .thenThrow(new ResourceNotFoundException("Payment not found with id: 99"));

        mockMvc.perform(get("/api/payments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentByOrderId_found_returns200() throws Exception {
        when(paymentService.getPaymentByOrderId(1L)).thenReturn(paymentDTO);

        mockMvc.perform(get("/api/payments/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void getPaymentByOrderId_notFound_returns404() throws Exception {
        when(paymentService.getPaymentByOrderId(99L))
                .thenThrow(new ResourceNotFoundException("Payment not found for order id: 99"));

        mockMvc.perform(get("/api/payments/order/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllPayments_returns200WithList() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of(paymentDTO));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].paymentMethod").value("CREDIT_CARD"));
    }

    @Test
    void getPaymentsByStatus_returns200() throws Exception {
        when(paymentService.getPaymentsByStatus(PaymentStatus.COMPLETED)).thenReturn(List.of(paymentDTO));

        mockMvc.perform(get("/api/payments/status/COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        PaymentDTO updated = PaymentDTO.builder().id(1L).orderId(1L)
                .paymentMethod(PaymentMethod.CREDIT_CARD).status(PaymentStatus.FAILED)
                .amount(new BigDecimal("999.99")).currency("USD").build();
        when(paymentService.updatePaymentStatus(1L, PaymentStatus.FAILED)).thenReturn(updated);

        mockMvc.perform(patch("/api/payments/1/status").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void refundPayment_returns200() throws Exception {
        PaymentDTO refunded = PaymentDTO.builder().id(1L).orderId(1L)
                .paymentMethod(PaymentMethod.CREDIT_CARD).status(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("999.99")).currency("USD").build();
        when(paymentService.refundPayment(1L)).thenReturn(refunded);

        mockMvc.perform(patch("/api/payments/1/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundPayment_notFound_returns404() throws Exception {
        when(paymentService.refundPayment(99L))
                .thenThrow(new ResourceNotFoundException("Payment not found with id: 99"));

        mockMvc.perform(patch("/api/payments/99/refund"))
                .andExpect(status().isNotFound());
    }
}
