package com.ecommerce.controller;

import com.ecommerce.dto.CartDTO;
import com.ecommerce.dto.CartItemDTO;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.service.CartService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CartService cartService;

    private CartDTO cartDTO;
    private CartItemDTO cartItemDTO;

    @BeforeEach
    void setUp() {
        cartItemDTO = CartItemDTO.builder()
                .id(1L)
                .productId(1L)
                .productName("Laptop")
                .quantity(2)
                .unitPrice(new BigDecimal("999.99"))
                .subtotal(new BigDecimal("1999.98"))
                .build();

        cartDTO = CartDTO.builder()
                .id(1L)
                .userId(1L)
                .cartItems(List.of(cartItemDTO))
                .totalAmount(new BigDecimal("1999.98"))
                .build();
    }

    @Test
    void getCart_found_returns200() throws Exception {
        when(cartService.getCartByUserId(1L)).thenReturn(cartDTO);

        mockMvc.perform(get("/api/cart/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.cartItems", hasSize(1)))
                .andExpect(jsonPath("$.cartItems[0].productName").value("Laptop"));
    }

    @Test
    void getCart_userNotFound_returns404() throws Exception {
        when(cartService.getCartByUserId(99L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/cart/user/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_validInput_returns200() throws Exception {
        CartItemDTO requestItem = CartItemDTO.builder().productId(1L).quantity(2).build();
        when(cartService.addItemToCart(eq(1L), any(CartItemDTO.class))).thenReturn(cartDTO);

        mockMvc.perform(post("/api/cart/user/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(1)));
    }

    @Test
    void addItem_missingProductId_returns400() throws Exception {
        CartItemDTO invalid = CartItemDTO.builder().quantity(2).build();

        mockMvc.perform(post("/api/cart/user/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_quantityLessThanOne_returns400() throws Exception {
        CartItemDTO invalid = CartItemDTO.builder().productId(1L).quantity(0).build();

        mockMvc.perform(post("/api/cart/user/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_productNotFound_returns404() throws Exception {
        CartItemDTO requestItem = CartItemDTO.builder().productId(99L).quantity(1).build();
        when(cartService.addItemToCart(eq(1L), any(CartItemDTO.class)))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(post("/api/cart/user/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestItem)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_returns200() throws Exception {
        when(cartService.updateCartItem(1L, 1L, 5)).thenReturn(cartDTO);

        mockMvc.perform(put("/api/cart/user/1/items/1").param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void updateItem_cartNotFound_returns404() throws Exception {
        when(cartService.updateCartItem(99L, 1L, 5))
                .thenThrow(new ResourceNotFoundException("Cart not found for user id: 99"));

        mockMvc.perform(put("/api/cart/user/99/items/1").param("quantity", "5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeItem_returns200() throws Exception {
        CartDTO updatedCart = CartDTO.builder().id(1L).userId(1L)
                .cartItems(List.of()).totalAmount(BigDecimal.ZERO).build();
        when(cartService.removeItemFromCart(1L, 1L)).thenReturn(updatedCart);

        mockMvc.perform(delete("/api/cart/user/1/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(0)));
    }

    @Test
    void removeItem_itemNotFound_returns404() throws Exception {
        when(cartService.removeItemFromCart(1L, 99L))
                .thenThrow(new ResourceNotFoundException("Cart item not found with id: 99"));

        mockMvc.perform(delete("/api/cart/user/1/items/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_returns204() throws Exception {
        doNothing().when(cartService).clearCart(1L);

        mockMvc.perform(delete("/api/cart/user/1/clear"))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearCart_cartNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Cart not found for user id: 99"))
                .when(cartService).clearCart(99L);

        mockMvc.perform(delete("/api/cart/user/99/clear"))
                .andExpect(status().isNotFound());
    }
}
