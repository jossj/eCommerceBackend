package au.com.j2econsulting.controller;

import au.com.j2econsulting.dto.CartDTO;
import au.com.j2econsulting.dto.CartItemDTO;
import au.com.j2econsulting.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<CartDTO> getCart(@PathVariable Long userId) {
        return ResponseEntity.ok(cartService.getCartByUserId(userId));
    }

    @PostMapping("/user/{userId}/items")
    public ResponseEntity<CartDTO> addItem(@PathVariable Long userId,
                                           @Valid @RequestBody CartItemDTO cartItemDTO) {
        return ResponseEntity.ok(cartService.addItemToCart(userId, cartItemDTO));
    }

    @PutMapping("/user/{userId}/items/{itemId}")
    public ResponseEntity<CartDTO> updateItem(@PathVariable Long userId,
                                              @PathVariable Long itemId,
                                              @RequestParam int quantity) {
        return ResponseEntity.ok(cartService.updateCartItem(userId, itemId, quantity));
    }

    @DeleteMapping("/user/{userId}/items/{itemId}")
    public ResponseEntity<CartDTO> removeItem(@PathVariable Long userId,
                                              @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeItemFromCart(userId, itemId));
    }

    @DeleteMapping("/user/{userId}/clear")
    public ResponseEntity<Void> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
