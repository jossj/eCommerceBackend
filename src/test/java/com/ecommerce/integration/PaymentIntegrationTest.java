package com.ecommerce.integration;

import com.ecommerce.dto.*;
import com.ecommerce.entity.Payment.PaymentMethod;
import com.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private Long orderId;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        UserDTO user = UserDTO.builder()
                .firstName("Pay").lastName("User")
                .email("payuser@test.com").password("password").build();
        String userResp = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readValue(userResp, UserDTO.class).getId();

        ProductDTO product = ProductDTO.builder()
                .name("Pay Product").price(new BigDecimal("250.00"))
                .stockQuantity(10).active(true).build();
        String productResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readValue(productResp, ProductDTO.class).getId();

        OrderDTO order = OrderDTO.builder()
                .userId(userId).shippingAddress("1 Pay Lane")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(2).build()))
                .build();
        String orderResp = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andReturn().getResponse().getContentAsString();
        orderId = objectMapper.readValue(orderResp, OrderDTO.class).getId();
    }

    @Test
    void processPayment_createsPaymentAndConfirmsOrder() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .currency("AUD")
                .build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.currency").value("AUD"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(500.00));

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void processPayment_defaultsCurrencyToAud() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId)
                .paymentMethod(PaymentMethod.PAYPAL)
                .build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("AUD"));
    }

    @Test
    void processPayment_duplicatePayment_returns409() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.CREDIT_CARD).build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(orderId.toString())));
    }

    @Test
    void processPayment_orderNotFound_returns404() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(999999L).paymentMethod(PaymentMethod.CREDIT_CARD).build();

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentById_found_returns200() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.DEBIT_CARD).build();
        String created = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readValue(created, PaymentDTO.class).getId();

        mockMvc.perform(get("/api/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));
    }

    @Test
    void getPaymentById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/payments/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentByOrderId_found_returns200() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.PAYPAL).build();
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/payments/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    void getAllPayments_returnsAllPayments() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.BANK_TRANSFER).build();
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getPaymentsByStatus_returnsFilteredPayments() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/payments/status/COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void updatePaymentStatus_changesStatus() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        String created = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readValue(created, PaymentDTO.class).getId();

        mockMvc.perform(patch("/api/payments/" + paymentId + "/status").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void refundPayment_setsPaymentRefundedAndOrderRefunded() throws Exception {
        PaymentDTO request = PaymentDTO.builder()
                .orderId(orderId).paymentMethod(PaymentMethod.CREDIT_CARD).build();
        String created = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long paymentId = objectMapper.readValue(created, PaymentDTO.class).getId();

        mockMvc.perform(patch("/api/payments/" + paymentId + "/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundPayment_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/payments/999999/refund"))
                .andExpect(status().isNotFound());
    }
}
