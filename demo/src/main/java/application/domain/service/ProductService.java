package application.domain.service;

import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.repository.ProductRepository;
import application.persistence.mysql.repository.ProductVariantRepository;
import application.domain.enums.ProductStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
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
        // Carga junto con el vendedor: los controladores exponen datos del
        // vendedor despues de cerrar la transaccion (open-in-view=false).
        return productRepository.findByIdWithSeller(id);
    }

    public List<ProductEntity> findBySellerId(Long sellerId) {
        return productRepository.findBySellerId(sellerId);
    }

    public List<ProductEntity> findAllProducts() {
        return productRepository.findAll();
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

    /**
     * Valida que el producto cumpla los datos obligatorios basicos antes de
     * registrarse o publicarse (regla del dominio Product).
     */
    private void validateRequiredData(ProductEntity product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BusinessRuleException("El nombre del producto es obligatorio");
        }
        if (product.getDescription() == null || product.getDescription().isBlank()) {
            throw new BusinessRuleException("La descripcion del producto es obligatoria");
        }
        if (product.getBasePrice() == null || product.getBasePrice().signum() <= 0) {
            throw new BusinessRuleException("El precio base del producto debe ser mayor que cero");
        }
        if (product.getCategory() == null || product.getCategory().isBlank()) {
            throw new BusinessRuleException("La categoria del producto es obligatoria");
        }
        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new BusinessRuleException("El SKU del producto es obligatorio");
        }
        if (product.getSeller() == null || product.getSeller().getId() == null) {
            throw new BusinessRuleException("El producto debe estar asociado a un vendedor valido");
        }
    }

    /**
     * RegisterProduct: registra un producto nuevo.
     * Los productos se registran en estado SUSPENDED; el vendedor los publica
     * explicitamente cuando estan listos (flujo draft -> publish).
     * El estado debe pertenecer a ProductStatus.
     */
    public ProductEntity registerProduct(ProductEntity product) {
        validateRequiredData(product);
        if (productRepository.existsBySku(product.getSku())) {
            throw new BusinessRuleException("Ya existe un producto con el SKU: " + product.getSku());
        }
        if (product.getStatus() == null || product.getStatus() != ProductStatus.SUSPENDED) {
            product.setStatus(ProductStatus.SUSPENDED);
        }
        return productRepository.save(product);
    }

    /**
     * UpdateProduct: actualiza los datos del producto. Un vendedor solo puede
     * actualizar sus propios productos (el controlador valida propiedad/admin).
     */
    public ProductEntity updateProduct(ProductEntity product) {
        ProductEntity existing = productRepository.findById(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + product.getId()));
        if (existing.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("Un producto descontinuado no puede actualizarse");
        }
        validateRequiredData(product);
        return productRepository.save(existing);
    }

    /**
     * PublishProduct: publica un producto suspendido. Debe cumplir los datos
     * obligatorios y no puede estar descontinuado. Publicar no garantiza
     * disponibilidad: para productos fisicos la disponibilidad depende del inventario.
     */
    public ProductEntity publishProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + productId));
        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("Un producto descontinuado no puede publicarse");
        }
        if (product.getStatus() == ProductStatus.PUBLISHED) {
            throw new BusinessRuleException("El producto ya se encuentra publicado");
        }
        validateRequiredData(product);
        product.setStatus(ProductStatus.PUBLISHED);
        return productRepository.save(product);
    }

    /**
     * SuspendProduct: retira temporalmente un producto publicado.
     * Un producto suspendido no puede incluirse en nuevas compras.
     */
    public ProductEntity suspendProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + productId));
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BusinessRuleException("Solo un producto publicado puede suspenderse");
        }
        product.setStatus(ProductStatus.SUSPENDED);
        return productRepository.save(product);
    }

    /**
     * DiscontinueProduct: retira definitivamente el producto de la plataforma.
     */
    public ProductEntity discontinueProduct(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + productId));
        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("El producto ya se encuentra descontinuado");
        }
        product.setStatus(ProductStatus.DISCONTINUED);
        return productRepository.save(product);
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