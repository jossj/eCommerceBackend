package com.ecommerce.controller;

import com.ecommerce.dto.OrderDTO;
import com.ecommerce.dto.OrderItemDTO;
import com.ecommerce.entity.Order.OrderStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private OrderService orderService;

    private OrderDTO orderDTO;

    @BeforeEach
    void setUp() {
        OrderItemDTO itemDTO = OrderItemDTO.builder()
                .id(1L).productId(1L).productName("Laptop")
                .quantity(2).unitPrice(new BigDecimal("999.99"))
                .subtotal(new BigDecimal("1999.98"))
                .build();

        orderDTO = OrderDTO.builder()
                .id(1L)
                .userId(1L)
                .orderItems(List.of(itemDTO))
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("1999.98"))
                .shippingAddress("123 Main St")
                .build();
    }

    @Test
    void createOrder_validInput_returns201() throws Exception {
        OrderDTO request = OrderDTO.builder()
                .userId(1L)
                .orderItems(List.of(OrderItemDTO.builder().productId(1L).quantity(2).build()))
                .shippingAddress("123 Main St")
                .build();
        when(orderService.createOrder(any(OrderDTO.class))).thenReturn(orderDTO);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_missingUserId_returns400() throws Exception {
        OrderDTO invalid = OrderDTO.builder()
                .orderItems(List.of()).shippingAddress("addr").build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrderFromCart_returns201() throws Exception {
        when(orderService.createOrderFromCart(1L, "123 Main St")).thenReturn(orderDTO);

        mockMvc.perform(post("/api/orders/from-cart/1")
                        .param("shippingAddress", "123 Main St"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void createOrderFromCart_emptyCart_returns400() throws Exception {
        when(orderService.createOrderFromCart(1L, "addr"))
                .thenThrow(new IllegalStateException("Cannot create order from an empty cart"));

        mockMvc.perform(post("/api/orders/from-cart/1").param("shippingAddress", "addr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderById_found_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(orderDTO);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalAmount").value(1999.98));
    }

    @Test
    void getOrderById_notFound_returns404() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllOrders_returns200WithList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(orderDTO));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getOrdersByUser_returns200() throws Exception {
        when(orderService.getOrdersByUserId(1L)).thenReturn(List.of(orderDTO));

        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void getOrdersByStatus_returns200() throws Exception {
        when(orderService.getOrdersByStatus(OrderStatus.PENDING)).thenReturn(List.of(orderDTO));

        mockMvc.perform(get("/api/orders/status/PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void updateStatus_returns200() throws Exception {
        OrderDTO updated = OrderDTO.builder().id(1L).userId(1L)
                .status(OrderStatus.SHIPPED).totalAmount(new BigDecimal("1999.98"))
                .orderItems(List.of()).build();
        when(orderService.updateOrderStatus(1L, OrderStatus.SHIPPED)).thenReturn(updated);

        mockMvc.perform(patch("/api/orders/1/status").param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateStatus_orderNotFound_returns404() throws Exception {
        when(orderService.updateOrderStatus(99L, OrderStatus.SHIPPED))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(patch("/api/orders/99/status").param("status", "SHIPPED"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTracking_returns200() throws Exception {
        OrderDTO updated = OrderDTO.builder().id(1L).userId(1L)
                .status(OrderStatus.SHIPPED).totalAmount(new BigDecimal("1999.98"))
                .trackingNumber("TRACK-123").orderItems(List.of()).build();
        when(orderService.updateTrackingNumber(1L, "TRACK-123")).thenReturn(updated);

        mockMvc.perform(patch("/api/orders/1/tracking").param("trackingNumber", "TRACK-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-123"));
    }

    @Test
    void cancelOrder_returns204() throws Exception {
        doNothing().when(orderService).cancelOrder(1L);

        mockMvc.perform(patch("/api/orders/1/cancel"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelOrder_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Order not found with id: 99"))
                .when(orderService).cancelOrder(99L);

        mockMvc.perform(patch("/api/orders/99/cancel"))
                .andExpect(status().isNotFound());
    }
}
