package com.ecommerce.service;

import com.ecommerce.dto.CartDTO;
import com.ecommerce.dto.CartItemDTO;

public interface CartService {

    CartDTO getCartByUserId(Long userId);

    CartDTO addItemToCart(Long userId, CartItemDTO cartItemDTO);

    CartDTO updateCartItem(Long userId, Long cartItemId, int quantity);

    CartDTO removeItemFromCart(Long userId, Long cartItemId);

    void clearCart(Long userId);
}
