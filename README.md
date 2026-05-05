# eCommerce Backend

A RESTful API backend for an eCommerce platform built with Spring Boot 3.5.14, Spring Data JPA, and MySQL.

## Tech Stack

- **Java 21**
- **Spring Boot 3.5.14**
- **Spring Data JPA / Hibernate**
- **MySQL**
- **Lombok**
- **Maven**

## Prerequisites

- Java 21+
- Maven 3.8+
- MySQL 8.0+

## Setup

1. Create the database:
   ```sql
   CREATE DATABASE ecommerce_db;
   ```

2. Configure credentials in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
   spring.datasource.username=root
   spring.datasource.password=root
   ```

3. Build and run:
   ```bash
   mvn spring-boot:run
   ```

The server starts on **http://localhost:8080**.

Hibernate will auto-create/update tables on startup (`spring.jpa.hibernate.ddl-auto=update`).

## API Reference

### Users — `/api/users`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/users` | List all users |
| `GET` | `/api/users/{id}` | Get user by ID |
| `GET` | `/api/users/email/{email}` | Get user by email |
| `PUT` | `/api/users/{id}` | Update user |
| `DELETE` | `/api/users/{id}` | Delete user |

User roles: `CUSTOMER`, `ADMIN`

### Products — `/api/products`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/products` | Create a product |
| `GET` | `/api/products` | List all products |
| `GET` | `/api/products/active` | List active products |
| `GET` | `/api/products/{id}` | Get product by ID |
| `GET` | `/api/products/category/{category}` | Filter by category |
| `GET` | `/api/products/search?name=` | Search by name |
| `PUT` | `/api/products/{id}` | Update product |
| `PATCH` | `/api/products/{id}/stock?quantity=` | Update stock quantity |
| `DELETE` | `/api/products/{id}` | Delete product |

### Cart — `/api/cart`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/cart/user/{userId}` | Get cart for user |
| `POST` | `/api/cart/user/{userId}/items` | Add item to cart |
| `PUT` | `/api/cart/user/{userId}/items/{itemId}?quantity=` | Update item quantity |
| `DELETE` | `/api/cart/user/{userId}/items/{itemId}` | Remove item from cart |
| `DELETE` | `/api/cart/user/{userId}/clear` | Clear cart |

### Orders — `/api/orders`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/orders` | Create order manually |
| `POST` | `/api/orders/from-cart/{userId}?shippingAddress=` | Create order from cart |
| `GET` | `/api/orders` | List all orders |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `GET` | `/api/orders/user/{userId}` | Get orders for user |
| `GET` | `/api/orders/status/{status}` | Filter by status |
| `PATCH` | `/api/orders/{id}/status?status=` | Update order status |
| `PATCH` | `/api/orders/{id}/tracking?trackingNumber=` | Set tracking number |
| `PATCH` | `/api/orders/{id}/cancel` | Cancel order |

Order statuses: `PENDING`, `CONFIRMED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED`

### Payments — `/api/payments`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/payments` | Process a payment |
| `GET` | `/api/payments` | List all payments |
| `GET` | `/api/payments/{id}` | Get payment by ID |
| `GET` | `/api/payments/order/{orderId}` | Get payment for an order |
| `GET` | `/api/payments/status/{status}` | Filter by status |
| `PATCH` | `/api/payments/{id}/status?status=` | Update payment status |
| `PATCH` | `/api/payments/{id}/refund` | Refund a payment |

Payment statuses: `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`, `CANCELLED`

Payment methods: `CREDIT_CARD`, `DEBIT_CARD`, `PAYPAL`, `BANK_TRANSFER`, `CASH_ON_DELIVERY`

## Project Structure

```
src/main/java/com/ecommerce/
├── ECommerceApplication.java
├── config/          # Jackson configuration
├── controller/      # REST controllers
├── dto/             # Data transfer objects
├── entity/          # JPA entities
├── exception/       # Global exception handling
├── repository/      # Spring Data JPA repositories
└── service/         # Business logic (interfaces + impl/)
```

## Building

```bash
mvn clean package
java -jar target/ecommerce-backend-1.0.0.jar
```
