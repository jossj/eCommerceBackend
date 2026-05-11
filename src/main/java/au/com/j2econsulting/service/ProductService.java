package au.com.j2econsulting.service;

import au.com.j2econsulting.dto.ProductDTO;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productDTO);

    ProductDTO getProductById(Long id);

    List<ProductDTO> getAllProducts();

    List<ProductDTO> getActiveProducts();

    List<ProductDTO> getProductsByCategory(String category);

    List<ProductDTO> searchProductsByName(String name);

    ProductDTO updateProduct(Long id, ProductDTO productDTO);

    ProductDTO updateStock(Long id, int quantity);

    void deleteProduct(Long id);
}
