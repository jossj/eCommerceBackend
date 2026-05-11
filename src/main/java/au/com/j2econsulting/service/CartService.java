package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.CartDTO;
import au.com.j2econsulting.dto.CartItemDTO;

public interface CartService {

    CartDTO getCartByUserId(Long userId);

    CartDTO addItemToCart(Long userId, CartItemDTO cartItemDTO);

    CartDTO updateCartItem(Long userId, Long cartItemId, int quantity);

    CartDTO removeItemFromCart(Long userId, Long cartItemId);

    void clearCart(Long userId);
}
