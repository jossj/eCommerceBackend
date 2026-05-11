package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.OrderDTO;
import au.com.j2econsulting.entity.Order.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderDTO createOrderFromCart(Long userId, String shippingAddress);

    OrderDTO createOrder(OrderDTO orderDTO);

    OrderDTO getOrderById(Long id);

    List<OrderDTO> getAllOrders();

    List<OrderDTO> getOrdersByUserId(Long userId);

    List<OrderDTO> getOrdersByStatus(OrderStatus status);

    OrderDTO updateOrderStatus(Long id, OrderStatus status);

    OrderDTO updateTrackingNumber(Long id, String trackingNumber);

    void cancelOrder(Long id);
}
