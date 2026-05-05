package com.ecommerce.service;

import com.ecommerce.dto.OrderDTO;
import com.ecommerce.dto.OrderItemDTO;
import com.ecommerce.entity.*;
import com.ecommerce.entity.Order.OrderStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;
    private Order order;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).firstName("John").lastName("Doe")
                .email("john@example.com").password("password").role(User.Role.CUSTOMER)
                .build();

        product = Product.builder()
                .id(1L).name("Laptop")
                .price(new BigDecimal("999.99")).stockQuantity(10).active(true)
                .build();

        cart = Cart.builder().id(1L).user(user).build();
        cartItem = CartItem.builder()
                .id(1L).cart(cart).product(product)
                .quantity(2).unitPrice(new BigDecimal("999.99"))
                .build();
        cart.getCartItems().add(cartItem);

        OrderItem orderItem = OrderItem.builder()
                .id(1L).product(product).quantity(2).unitPrice(new BigDecimal("999.99"))
                .build();

        order = Order.builder()
                .id(1L).user(user)
                .totalAmount(new BigDecimal("1999.98"))
                .shippingAddress("123 Main St")
                .status(OrderStatus.PENDING)
                .build();
        order.getOrderItems().add(orderItem);
        orderItem.setOrder(order);
    }

    @Test
    void createOrderFromCart_success() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        OrderDTO result = orderService.createOrderFromCart(1L, "123 Main St");

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(cart.getCartItems()).isEmpty();
        verify(orderRepository).save(any(Order.class));
        verify(cartRepository).save(cart);
    }

    @Test
    void createOrderFromCart_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrderFromCart(99L, "address"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createOrderFromCart_emptyCart_throwsIllegalStateException() {
        Cart emptyCart = Cart.builder().id(2L).user(user).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> orderService.createOrderFromCart(1L, "address"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    void createOrder_success() {
        OrderItemDTO itemDTO = OrderItemDTO.builder()
                .productId(1L).quantity(2).build();
        OrderDTO dto = OrderDTO.builder()
                .userId(1L)
                .shippingAddress("123 Main St")
                .orderItems(List.of(itemDTO))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderDTO result = orderService.createOrder(dto);

        assertThat(result.getUserId()).isEqualTo(1L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_userNotFound_throwsException() {
        OrderDTO dto = OrderDTO.builder()
                .userId(99L).orderItems(List.of()).shippingAddress("addr").build();
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createOrder_productNotFound_throwsException() {
        OrderItemDTO itemDTO = OrderItemDTO.builder().productId(99L).quantity(1).build();
        OrderDTO dto = OrderDTO.builder()
                .userId(1L).orderItems(List.of(itemDTO)).shippingAddress("addr").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getOrderById_found() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void getOrderById_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllOrders_returnsMappedList() {
        when(orderRepository.findAll()).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getAllOrders();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getAllOrders_emptyRepo_returnsEmptyList() {
        when(orderRepository.findAll()).thenReturn(List.of());

        assertThat(orderService.getAllOrders()).isEmpty();
    }

    @Test
    void getOrdersByUserId_returnsFilteredList() {
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getOrdersByUserId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getOrdersByStatus_returnsFilteredList() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));

        List<OrderDTO> result = orderService.getOrdersByStatus(OrderStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void updateOrderStatus_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderDTO result = orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderStatus_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderStatus(99L, OrderStatus.SHIPPED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTrackingNumber_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.updateTrackingNumber(1L, "TRACK-XYZ-123");

        assertThat(order.getTrackingNumber()).isEqualTo("TRACK-XYZ-123");
    }

    @Test
    void updateTrackingNumber_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateTrackingNumber(99L, "TRACK"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelOrder_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_notFound_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createOrderFromCart_totalAmountComputedFromCartItems() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1999.98"));
            return order;
        });
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        orderService.createOrderFromCart(1L, "123 Main St");
    }
}
