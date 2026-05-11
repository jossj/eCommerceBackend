package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.ProductDTO;
import au.com.j2econsulting.entity.Product;
import au.com.j2econsulting.exception.ResourceNotFoundException;
import au.com.j2econsulting.repository.ProductRepository;
import au.com.j2econsulting.service.impl.ProductServiceImpl;
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
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private ProductDTO productDTO;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("Laptop")
                .description("A great laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category("Electronics")
                .active(true)
                .build();

        productDTO = ProductDTO.builder()
                .name("Laptop")
                .description("A great laptop")
                .price(new BigDecimal("999.99"))
                .stockQuantity(10)
                .category("Electronics")
                .active(true)
                .build();
    }

    @Test
    void createProduct_success() {
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductDTO result = productService.createProduct(productDTO);

        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
        assertThat(result.getCategory()).isEqualTo("Electronics");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_defaultsActiveTrue_whenNull() {
        productDTO.setActive(null);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            assertThat(saved.getActive()).isTrue();
            return product;
        });

        productService.createProduct(productDTO);
    }

    @Test
    void createProduct_defaultsStockToZero_whenNull() {
        productDTO.setStockQuantity(null);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            assertThat(saved.getStockQuantity()).isEqualTo(0);
            return product;
        });

        productService.createProduct(productDTO);
    }

    @Test
    void getProductById_found() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductDTO result = productService.getProductById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Laptop");
    }

    @Test
    void getProductById_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllProducts_returnsMappedList() {
        Product second = Product.builder().id(2L).name("Phone")
                .price(new BigDecimal("499.99")).stockQuantity(5).category("Electronics").active(true).build();
        when(productRepository.findAll()).thenReturn(List.of(product, second));

        List<ProductDTO> result = productService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProductDTO::getName).containsExactly("Laptop", "Phone");
    }

    @Test
    void getAllProducts_emptyRepo_returnsEmptyList() {
        when(productRepository.findAll()).thenReturn(List.of());

        assertThat(productService.getAllProducts()).isEmpty();
    }

    @Test
    void getActiveProducts_returnsOnlyActive() {
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));

        List<ProductDTO> result = productService.getActiveProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActive()).isTrue();
    }

    @Test
    void getProductsByCategory_returnsFiltered() {
        when(productRepository.findByCategory("Electronics")).thenReturn(List.of(product));

        List<ProductDTO> result = productService.getProductsByCategory("Electronics");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Electronics");
    }

    @Test
    void getProductsByCategory_noMatch_returnsEmptyList() {
        when(productRepository.findByCategory("NonExistent")).thenReturn(List.of());

        assertThat(productService.getProductsByCategory("NonExistent")).isEmpty();
    }

    @Test
    void searchProductsByName_returnsMatching() {
        when(productRepository.findByNameContainingIgnoreCase("lap")).thenReturn(List.of(product));

        List<ProductDTO> result = productService.searchProductsByName("lap");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    void updateProduct_success() {
        ProductDTO updateDTO = ProductDTO.builder()
                .name("Gaming Laptop")
                .description("High performance")
                .price(new BigDecimal("1299.99"))
                .category("Gaming")
                .imageUrl("http://img.test/laptop.jpg")
                .active(false)
                .stockQuantity(5)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, updateDTO);

        assertThat(product.getName()).isEqualTo("Gaming Laptop");
        assertThat(product.getDescription()).isEqualTo("High performance");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(product.getCategory()).isEqualTo("Gaming");
        assertThat(product.getActive()).isFalse();
        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_nullActive_doesNotChangeActive() {
        ProductDTO updateDTO = ProductDTO.builder()
                .name("Updated").price(new BigDecimal("100")).category("X").active(null).build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateProduct(1L, updateDTO);

        assertThat(product.getActive()).isTrue();
    }

    @Test
    void updateProduct_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, productDTO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateStock_success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.updateStock(1L, 50);

        assertThat(product.getStockQuantity()).isEqualTo(50);
        verify(productRepository).save(product);
    }

    @Test
    void updateStock_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateStock(99L, 10))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteProduct_success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doNothing().when(productRepository).deleteById(1L);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }
}
