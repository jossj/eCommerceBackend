package com.ecommerce.integration;

import com.ecommerce.config.TestSecurityConfig;
import com.ecommerce.dto.*;
import com.ecommerce.entity.Order.OrderStatus;
import com.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import(TestSecurityConfig.class)
@Transactional
@Rollback
class OrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        UserDTO user = UserDTO.builder()
                .firstName("Order").lastName("User")
                .email("orderuser@test.com").password("password").build();
        String userResp = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readValue(userResp, UserDTO.class).getId();

        ProductDTO product = ProductDTO.builder()
                .name("Order Product").price(new BigDecimal("100.00"))
                .stockQuantity(50).active(true).build();
        String productResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readValue(productResp, ProductDTO.class).getId();
    }

    @Test
    void createOrder_persistsAndReturnsCreatedOrder() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId)
                .shippingAddress("123 Test St")
                .orderItems(List.of(
                        OrderItemDTO.builder().productId(productId).quantity(2).build()
                ))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderItems", hasSize(1)))
                .andExpect(jsonPath("$.totalAmount").value(200.00));
    }

    @Test
    void createOrderFromCart_createsOrderAndClearsCart() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(3).build();
        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/from-cart/" + userId)
                        .param("shippingAddress", "456 Cart St"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.totalAmount").value(300.00))
                .andExpect(jsonPath("$.shippingAddress").value("456 Cart St"))
                .andExpect(jsonPath("$.orderItems", hasSize(1)));

        mockMvc.perform(get("/api/cart/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(0)));
    }

    @Test
    void createOrderFromCart_emptyCart_returns400() throws Exception {
        // Ensure the user has a cart (cart is created lazily on first GET)
        mockMvc.perform(get("/api/cart/user/" + userId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/from-cart/" + userId)
                        .param("shippingAddress", "addr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("empty cart")));
    }

    @Test
    void getOrderById_found_returns200() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readValue(created, OrderDTO.class).getId();

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllOrders_returnsAllCreatedOrders() throws Exception {
        for (int i = 0; i < 2; i++) {
            OrderDTO request = OrderDTO.builder()
                    .userId(userId).shippingAddress("addr " + i)
                    .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                    .build();
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getOrdersByUser_returnsUserOrders() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(userId));
    }

    @Test
    void getOrdersByStatus_returnsFilteredOrders() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void updateOrderStatus_changesStatus() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readValue(created, OrderDTO.class).getId();

        mockMvc.perform(patch("/api/orders/" + orderId + "/status").param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateTrackingNumber_setsTrackingNumber() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readValue(created, OrderDTO.class).getId();

        mockMvc.perform(patch("/api/orders/" + orderId + "/tracking")
                        .param("trackingNumber", "TRACK-XYZ-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-XYZ-999"));
    }

    @Test
    void cancelOrder_setsStatusToCancelled() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();
        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readValue(created, OrderDTO.class).getId();

        mockMvc.perform(patch("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void createOrder_userNotFound_returns404() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(999999L).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(productId).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_productNotFound_returns404() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(userId).shippingAddress("addr")
                .orderItems(List.of(OrderItemDTO.builder().productId(999999L).quantity(1).build()))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
