package au.com.j2econsulting.service;

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
import au.com.j2econsulting.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;

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
    }

    @Test
    void getCartByUserId_existingCart_returnsMappedDTO() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getCartByUserId(1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getCartItems()).hasSize(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1999.98"));
    }

    @Test
    void getCartByUserId_noCart_createsAndReturnsNewCart() {
        Cart newCart = Cart.builder().id(2L).user(user).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

        CartDTO result = cartService.getCartByUserId(1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getCartItems()).isEmpty();
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void getCartByUserId_noCartNoUser_throwsException() {
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCartByUserId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void addItemToCart_newProduct_addsItemToCart() {
        Cart emptyCart = Cart.builder().id(1L).user(user).build();
        CartItemDTO dto = CartItemDTO.builder().productId(1L).quantity(3).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(emptyCart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(emptyCart);

        cartService.addItemToCart(1L, dto);

        assertThat(emptyCart.getCartItems()).hasSize(1);
        assertThat(emptyCart.getCartItems().get(0).getQuantity()).isEqualTo(3);
        verify(cartRepository).save(emptyCart);
    }

    @Test
    void addItemToCart_existingProduct_incrementsQuantity() {
        CartItemDTO dto = CartItemDTO.builder().productId(1L).quantity(3).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 1L)).thenReturn(Optional.of(cartItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.addItemToCart(1L, dto);

        assertThat(cartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    void addItemToCart_productNotFound_throwsException() {
        CartItemDTO dto = CartItemDTO.builder().productId(99L).quantity(1).build();

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItemToCart(1L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateCartItem_success() {
        Cart refreshedCart = Cart.builder().id(1L).user(user).build();
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(cartItem);
        when(cartRepository.findById(1L)).thenReturn(Optional.of(refreshedCart));

        cartService.updateCartItem(1L, 1L, 7);

        assertThat(cartItem.getQuantity()).isEqualTo(7);
        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void updateCartItem_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateCartItem(99L, 1L, 5))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateCartItem_itemNotFound_throwsException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateCartItem(1L, 99L, 5))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void removeItemFromCart_success() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.removeItemFromCart(1L, 1L);

        assertThat(cart.getCartItems()).doesNotContain(cartItem);
        verify(cartRepository).save(cart);
    }

    @Test
    void removeItemFromCart_itemNotFound_throwsException() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItemFromCart(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void clearCart_success() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.clearCart(1L);

        assertThat(cart.getCartItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void clearCart_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.clearCart(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
