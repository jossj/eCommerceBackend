package au.com.j2econsulting.service.impl;

import au.com.j2econsulting.dto.OrderDTO;
import au.com.j2econsulting.dto.OrderItemDTO;
import au.com.j2econsulting.entity.*;
import au.com.j2econsulting.entity.Order.OrderStatus;
import au.com.j2econsulting.exception.ResourceNotFoundException;
import au.com.j2econsulting.repository.*;
import au.com.j2econsulting.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    @Override
    public OrderDTO createOrderFromCart(Long userId, String shippingAddress) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user id: " + userId));

        if (cart.getCartItems().isEmpty()) {
            throw new IllegalStateException("Cannot create order from an empty cart");
        }

        User user = cart.getUser();
        Order order = Order.builder()
                .user(user)
                .shippingAddress(shippingAddress)
                .totalAmount(cart.getTotalAmount())
                .build();

        List<OrderItem> items = cart.getCartItems().stream().map(ci -> OrderItem.builder()
                .order(order)
                .product(ci.getProduct())
                .quantity(ci.getQuantity())
                .unitPrice(ci.getUnitPrice())
                .build()).collect(Collectors.toList());

        order.setOrderItems(items);
        Order saved = orderRepository.save(order);

        cart.getCartItems().clear();
        cartRepository.save(cart);

        return toDTO(saved);
    }

    @Override
    public OrderDTO createOrder(OrderDTO dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + dto.getUserId()));

        Order order = Order.builder()
                .user(user)
                .shippingAddress(dto.getShippingAddress())
                .build();

        List<OrderItem> items = dto.getOrderItems().stream().map(itemDTO -> {
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemDTO.getProductId()));
            return OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemDTO.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
        }).collect(Collectors.toList());

        order.setOrderItems(items);
        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);

        return toDTO(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public OrderDTO updateOrderStatus(Long id, OrderStatus status) {
        Order order = findOrThrow(id);
        order.setStatus(status);
        return toDTO(orderRepository.save(order));
    }

    @Override
    public OrderDTO updateTrackingNumber(Long id, String trackingNumber) {
        Order order = findOrThrow(id);
        order.setTrackingNumber(trackingNumber);
        return toDTO(orderRepository.save(order));
    }

    @Override
    public void cancelOrder(Long id) {
        Order order = findOrThrow(id);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderDTO toDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .orderItems(order.getOrderItems().stream().map(this::toItemDTO).collect(Collectors.toList()))
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .trackingNumber(order.getTrackingNumber())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemDTO toItemDTO(OrderItem item) {
        return OrderItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}
