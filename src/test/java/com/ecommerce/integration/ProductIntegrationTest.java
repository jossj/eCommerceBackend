package com.ecommerce.integration;

import com.ecommerce.dto.ProductDTO;
import com.ecommerce.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class ProductIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    private ProductDTO buildProductDTO(String name, String category, double price) {
        return ProductDTO.builder()
                .name(name).description("Test description")
                .price(new BigDecimal(price)).stockQuantity(100)
                .category(category).active(true)
                .build();
    }

    @Test
    void createProduct_persistsAndReturnsCreatedProduct() throws Exception {
        ProductDTO dto = buildProductDTO("Test Laptop", "Electronics", 999.99);

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Test Laptop"))
                .andExpect(jsonPath("$.category").value("Electronics"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readValue(response, ProductDTO.class).getId();
        assertThat(productRepository.findById(id)).isPresent();
    }

    @Test
    void getProductById_returnsExistingProduct() throws Exception {
        ProductDTO dto = buildProductDTO("Phone", "Electronics", 599.99);

        String created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(created, ProductDTO.class).getId();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phone"));
    }

    @Test
    void getProductById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllProducts_returnsAllCreatedProducts() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Product 1", "A", 10.0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Product 2", "B", 20.0))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getActiveProducts_returnsOnlyActiveProducts() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Active Product", "X", 50.0))))
                .andExpect(status().isCreated());

        ProductDTO inactive = buildProductDTO("Inactive Product", "X", 50.0);
        inactive.setActive(false);
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inactive)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Active Product"));
    }

    @Test
    void getProductsByCategory_returnsFilteredProducts() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Electronics Item", "Electronics", 100.0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Clothing Item", "Clothing", 30.0))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category").value("Electronics"));
    }

    @Test
    void searchProductsByName_returnsCaseInsensitiveMatches() throws Exception {
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Gaming Laptop Pro", "Electronics", 1500.0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Budget Laptop", "Electronics", 500.0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildProductDTO("Phone", "Electronics", 300.0))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/search").param("name", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void updateProduct_changesFieldsCorrectly() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO("Old Name", "Old Category", 100.0))))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(created, ProductDTO.class).getId();

        ProductDTO updateDTO = ProductDTO.builder()
                .name("New Name").description("Updated desc")
                .price(new BigDecimal("200.00")).category("New Category")
                .stockQuantity(50).active(false).build();

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.category").value("New Category"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateStock_changesStockQuantity() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO("Stocked Item", "X", 50.0))))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(created, ProductDTO.class).getId();

        mockMvc.perform(patch("/api/products/" + id + "/stock").param("quantity", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(250));
    }

    @Test
    void deleteProduct_removesFromDatabase() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO("To Delete", "X", 10.0))))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(created, ProductDTO.class).getId();

        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isNotFound());

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void createProduct_defaultsActiveToTrue_andStockToZero_whenNotProvided() throws Exception {
        ProductDTO dto = ProductDTO.builder()
                .name("Minimal Product").price(new BigDecimal("9.99")).build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.stockQuantity").value(0));
    }
}
