package application.application.service;

import application.application.persistence.mysql.entity.ProductEntity;
import application.application.persistence.mysql.entity.ProductVariantEntity;
import application.application.persistence.mysql.repository.ProductRepository;
import application.application.persistence.mysql.repository.ProductVariantRepository;
import application.application.domain.enums.ProductStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    public ProductEntity save(ProductEntity product) {
        return productRepository.save(product);
    }

    public Optional<ProductEntity> findById(Long id) {
        return productRepository.findById(id);
    }

    public List<ProductEntity> findBySellerId(Long sellerId) {
        return productRepository.findBySellerId(sellerId);
    }

    public List<ProductEntity> findByStatus(ProductStatus status) {
        return productRepository.findByStatus(status);
    }

    public Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable) {
        return productRepository.findByStatus(status, pageable);
    }

    public List<ProductEntity> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<ProductEntity> findBySellerIdAndStatus(Long sellerId, ProductStatus status) {
        return productRepository.findBySellerIdAndStatus(sellerId, status);
    }

    public List<ProductEntity> searchByKeyword(String keyword) {
        return productRepository.searchByKeyword(keyword);
    }

    public Optional<ProductEntity> findBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    // Variant operations
    public ProductVariantEntity saveVariant(ProductVariantEntity variant) {
        return variantRepository.save(variant);
    }

    public List<ProductVariantEntity> findVariantsByProductId(Long productId) {
        return variantRepository.findByProductId(productId);
    }

    public List<ProductVariantEntity> findActiveVariantsByProductId(Long productId) {
        return variantRepository.findByProductIdAndActiveTrue(productId);
    }

    public Optional<ProductVariantEntity> findVariantBySku(String sku) {
        return variantRepository.findBySku(sku);
    }

    public Optional<ProductVariantEntity> findVariantById(Long id) {
        return variantRepository.findById(id);
    }

    public void deleteVariantById(Long id) {
        variantRepository.deleteById(id);
    }
}