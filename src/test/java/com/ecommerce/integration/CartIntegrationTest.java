package com.ecommerce.integration;

import com.ecommerce.dto.CartItemDTO;
import com.ecommerce.dto.ProductDTO;
import com.ecommerce.dto.UserDTO;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class CartIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        UserDTO user = UserDTO.builder()
                .firstName("Cart").lastName("User")
                .email("cartuser@test.com").password("password").build();
        String userResp = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readValue(userResp, UserDTO.class).getId();

        ProductDTO product = ProductDTO.builder()
                .name("Test Product").price(new BigDecimal("50.00"))
                .stockQuantity(100).active(true).build();
        String productResp = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andReturn().getResponse().getContentAsString();
        productId = objectMapper.readValue(productResp, ProductDTO.class).getId();
    }

    @Test
    void getCart_createsEmptyCartForNewUser() throws Exception {
        mockMvc.perform(get("/api/cart/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.cartItems", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void addItemToCart_addsProductSuccessfully() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(2).build();

        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(1)))
                .andExpect(jsonPath("$.cartItems[0].productId").value(productId))
                .andExpect(jsonPath("$.cartItems[0].quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(100.00));
    }

    @Test
    void addItemToCart_sameProductTwice_incrementsQuantity() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(2).build();

        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk());

        CartItemDTO moreItems = CartItemDTO.builder().productId(productId).quantity(3).build();
        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moreItems)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(1)))
                .andExpect(jsonPath("$.cartItems[0].quantity").value(5))
                .andExpect(jsonPath("$.totalAmount").value(250.00));
    }

    @Test
    void addItemToCart_nonExistentProduct_returns404() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(999999L).quantity(1).build();

        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCartItem_changesQuantity() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(1).build();
        String addResp = mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andReturn().getResponse().getContentAsString();

        Long itemId = objectMapper.readTree(addResp)
                .get("cartItems").get(0).get("id").asLong();

        mockMvc.perform(put("/api/cart/user/" + userId + "/items/" + itemId)
                        .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems[0].quantity").value(10))
                .andExpect(jsonPath("$.totalAmount").value(500.00));
    }

    @Test
    void removeItemFromCart_removesProductFromCart() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(2).build();
        String addResp = mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andReturn().getResponse().getContentAsString();

        Long itemId = objectMapper.readTree(addResp)
                .get("cartItems").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/cart/user/" + userId + "/items/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void clearCart_emptiesAllItems() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(3).build();
        mockMvc.perform(post("/api/cart/user/" + userId + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart/user/" + userId + "/clear"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems", hasSize(0)));
    }

    @Test
    void addItemToCart_nonExistentUser_returns404() throws Exception {
        CartItemDTO item = CartItemDTO.builder().productId(productId).quantity(1).build();

        mockMvc.perform(post("/api/cart/user/999999/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isNotFound());
    }
}
