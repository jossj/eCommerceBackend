package com.ecommerce.config;

import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        seedUsers();
        seedProducts();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already seeded, skipping.");
            return;
        }

        List<User> users = List.of(
            User.builder()
                .firstName("Alice")
                .lastName("Admin")
                .email("alice@example.com")
                .password("admin1234")
                .phone("555-0100")
                .address("1 Admin Lane, Springfield")
                .role(User.Role.ADMIN)
                .build(),
            User.builder()
                .firstName("Bob")
                .lastName("Smith")
                .email("bob@example.com")
                .password("password123")
                .phone("555-0101")
                .address("42 Elm Street, Shelbyville")
                .build(),
            User.builder()
                .firstName("Carol")
                .lastName("Jones")
                .email("carol@example.com")
                .password("password123")
                .phone("555-0102")
                .address("7 Maple Ave, Capital City")
                .build(),
            User.builder()
                .firstName("David")
                .lastName("Lee")
                .email("david@example.com")
                .password("password123")
                .phone("555-0103")
                .address("99 Oak Road, Ogdenville")
                .build()
        );

        userRepository.saveAll(users);
        log.info("Seeded {} users.", users.size());
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            log.info("Products already seeded, skipping.");
            return;
        }

        List<Product> products = List.of(
            // Electronics
            Product.builder()
                .name("Wireless Noise-Cancelling Headphones")
                .description("Premium over-ear headphones with active noise cancellation and 30-hour battery life.")
                .price(new BigDecimal("149.99"))
                .stockQuantity(80)
                .category("Electronics")
                .build(),
            Product.builder()
                .name("4K Ultra HD Smart TV - 55\"")
                .description("55-inch 4K UHD smart TV with HDR support and built-in streaming apps.")
                .price(new BigDecimal("499.99"))
                .stockQuantity(30)
                .category("Electronics")
                .build(),
            Product.builder()
                .name("Mechanical Gaming Keyboard")
                .description("TKL mechanical keyboard with RGB backlighting and tactile switches.")
                .price(new BigDecimal("89.99"))
                .stockQuantity(120)
                .category("Electronics")
                .build(),

            // Clothing
            Product.builder()
                .name("Men's Classic Fit Chinos")
                .description("Versatile cotton-blend chinos available in multiple colours.")
                .price(new BigDecimal("39.99"))
                .stockQuantity(200)
                .category("Clothing")
                .build(),
            Product.builder()
                .name("Women's Lightweight Running Jacket")
                .description("Wind-resistant running jacket with reflective details and zip pockets.")
                .price(new BigDecimal("64.99"))
                .stockQuantity(150)
                .category("Clothing")
                .build(),
            Product.builder()
                .name("Unisex Graphic Tee")
                .description("100% organic cotton crew-neck tee with a bold print.")
                .price(new BigDecimal("24.99"))
                .stockQuantity(300)
                .category("Clothing")
                .build(),

            // Home & Kitchen
            Product.builder()
                .name("Stainless Steel Insulated Water Bottle")
                .description("32 oz double-walled bottle that keeps drinks cold for 24 hours.")
                .price(new BigDecimal("29.99"))
                .stockQuantity(250)
                .category("Home & Kitchen")
                .build(),
            Product.builder()
                .name("Non-Stick Ceramic Frying Pan Set")
                .description("3-piece ceramic-coated frying pan set, oven-safe up to 450°F.")
                .price(new BigDecimal("54.99"))
                .stockQuantity(90)
                .category("Home & Kitchen")
                .build(),
            Product.builder()
                .name("Bamboo Cutting Board")
                .description("Extra-large eco-friendly bamboo cutting board with juice groove.")
                .price(new BigDecimal("19.99"))
                .stockQuantity(180)
                .category("Home & Kitchen")
                .build(),

            // Books
            Product.builder()
                .name("Clean Code: A Handbook of Agile Software Craftsmanship")
                .description("Timeless guide to writing readable, maintainable code by Robert C. Martin.")
                .price(new BigDecimal("34.99"))
                .stockQuantity(60)
                .category("Books")
                .build(),
            Product.builder()
                .name("Designing Data-Intensive Applications")
                .description("Deep dive into the principles behind reliable, scalable distributed systems.")
                .price(new BigDecimal("49.99"))
                .stockQuantity(45)
                .category("Books")
                .build()
        );

        productRepository.saveAll(products);
        log.info("Seeded {} products.", products.size());
    }
}
