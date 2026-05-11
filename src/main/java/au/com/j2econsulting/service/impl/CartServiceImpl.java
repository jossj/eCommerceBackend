package au.com.j2econsulting.service.impl;

import au.com.j2econsulting.dto.CartDTO;
import au.com.j2econsulting.dto.CartItemDTO;
import au.com.j2econsulting.entity.Cart;
import au.com.j2econsulting.entity.CartItem;
import au.com.j2econsulting.entity.Product;
import au.com.j2econsulting.entity.User;
import au.com.j2econsulting.exception.ResourceNotFoundException;
import au.com.j2econsulting.repository.CartItemRepository;
import au.com.j2econsulting.repository.CartRepository;
import au.com.j2econsulting.repository.ProductRepository;
import au.com.j2econsulting.repository.UserRepository;
import au.com.j2econsulting.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public CartDTO getCartByUserId(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toDTO(cart);
    }

    @Override
    public CartDTO addItemToCart(Long userId, CartItemDTO dto) {
        Cart cart = getOrCreateCart(userId);
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + dto.getProductId()));

        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + dto.getQuantity()),
                        () -> cart.getCartItems().add(CartItem.builder()
                                .cart(cart)
                                .product(product)
                                .quantity(dto.getQuantity())
                                .unitPrice(product.getPrice())
                                .build())
                );

        return toDTO(cartRepository.save(cart));
    }

    @Override
    public CartDTO updateCartItem(Long userId, Long cartItemId, int quantity) {
        Cart cart = findCartOrThrow(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toDTO(cartRepository.findById(cart.getId()).orElseThrow());
    }

    @Override
    public CartDTO removeItemFromCart(Long userId, Long cartItemId) {
        Cart cart = findCartOrThrow(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));
        cart.getCartItems().remove(item);
        return toDTO(cartRepository.save(cart));
    }

    @Override
    public void clearCart(Long userId) {
        Cart cart = findCartOrThrow(userId);
        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
            return cartRepository.save(Cart.builder().user(user).build());
        });
    }

    private Cart findCartOrThrow(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user id: " + userId));
    }

    private CartDTO toDTO(Cart cart) {
        return CartDTO.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .cartItems(cart.getCartItems().stream().map(this::toItemDTO).collect(Collectors.toList()))
                .totalAmount(cart.getTotalAmount())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemDTO toItemDTO(CartItem item) {
        return CartItemDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}
