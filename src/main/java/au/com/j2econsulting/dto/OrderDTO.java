package au.com.j2econsulting.dto;

import au.com.j2econsulting.entity.Order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {

    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    private List<OrderItemDTO> orderItems;

    private OrderStatus status;

    private BigDecimal totalAmount;

    private String shippingAddress;

    private String trackingNumber;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
