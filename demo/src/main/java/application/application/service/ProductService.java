package application.service;

import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.DigitalProductEntity;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.repository.ProductRepository;
import application.persistence.mysql.repository.ProductVariantRepository;
import application.domain.enums.ProductStatus;
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

    // ==================== Domain Services (SDD - Product Services) ====================

    /**
     * RegisterProduct (Actor: Seller).
     * Registra un producto en el catálogo del vendedor.
     *
     * Reglas de negocio:
     * - Todo producto debe estar asociado a un vendedor.
     * - Debe cumplir los datos obligatorios de su tipo antes de registrarse.
     * - El SKU, si se indica, debe ser único.
     * - El estado inicial es {@code SUSPENDED}: el producto queda registrado
     *   pero aún no disponible; PublishProduct lo habilita en el catálogo.
     * - Registrar no garantiza unidades disponibles (eso lo controla Inventory).
     */
    public ProductEntity registerProduct(ProductEntity product) {
        if (product.getSeller() == null) {
            throw new BusinessRuleException("A product must be associated with a seller");
        }
        validateRequiredData(product);
        if (product.getSku() != null && !product.getSku().isBlank()
                && productRepository.existsBySku(product.getSku())) {
            throw new BusinessRuleException("SKU already exists: " + product.getSku());
        }

        product.setStatus(ProductStatus.SUSPENDED);
        return productRepository.save(product);
    }

    /**
     * UpdateProduct (Actor: Seller).
     * Actualiza la información permitida de un producto propio.
     *
     * Reglas de negocio:
     * - El producto debe existir y cumplir sus datos obligatorios.
     * - La validación de propiedad (solo productos propios / administrador)
     *   se realiza en la capa de API.
     * - El estado no se modifica aquí; se gestiona con Publish/Suspend/Discontinue.
     */
    public ProductEntity updateProduct(ProductEntity product) {
        if (product.getId() == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        validateRequiredData(product);
        return productRepository.save(product);
    }

    /**
     * PublishProduct (Actor: Seller).
     * Publica un producto para permitir que aparezca disponible en el catálogo.
     *
     * Reglas de negocio:
     * - El producto debe existir y cumplir sus datos obligatorios.
     * - No se republica un producto ya publicado.
     * - Publicar no garantiza unidades disponibles para productos físicos.
     */
    public ProductEntity publishProduct(Long productId) {
        ProductEntity product = findProductOrThrow(productId);
        validateRequiredData(product);

        if (product.getStatus() == ProductStatus.PUBLISHED) {
            throw new BusinessRuleException("Product is already published: " + productId);
        }

        product.setStatus(ProductStatus.PUBLISHED);
        return productRepository.save(product);
    }

    /**
     * SuspendProduct (Actor: Administrator / Seller).
     * Cambia el estado del producto a suspendido, si el actor tiene autorización.
     *
     * Reglas de negocio:
     * - Un producto suspendido no puede incluirse en nuevas compras.
     * - No se suspende un producto ya suspendido.
     */
    public ProductEntity suspendProduct(Long productId) {
        ProductEntity product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.SUSPENDED) {
            throw new BusinessRuleException("Product is already suspended: " + productId);
        }

        product.setStatus(ProductStatus.SUSPENDED);
        return productRepository.save(product);
    }

    /**
     * DiscontinueProduct (Actor: Administrator / Seller).
     * Cambia el estado del producto a descontinuado, si el actor tiene autorización.
     *
     * Reglas de negocio:
     * - Un producto descontinuado no puede incluirse en nuevas compras.
     * - No se descontinúa un producto ya descontinuado.
     */
    public ProductEntity discontinueProduct(Long productId) {
        ProductEntity product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("Product is already discontinued: " + productId);
        }

        product.setStatus(ProductStatus.DISCONTINUED);
        return productRepository.save(product);
    }

    private ProductEntity findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    /**
     * Valida los datos obligatorios comunes y los específicos del tipo de producto.
     * Se aplica antes de registrarse y antes de publicarse.
     */
    private void validateRequiredData(ProductEntity product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new BusinessRuleException("Product name is required");
        }
        if (product.getBasePrice() == null || product.getBasePrice().signum() <= 0) {
            throw new BusinessRuleException("Product base price must be greater than zero");
        }
        // Dato obligatorio por tipo: un producto digital necesita su descarga.
        if (product instanceof DigitalProductEntity digital
                && (digital.getDownloadUrl() == null || digital.getDownloadUrl().isBlank())) {
            throw new BusinessRuleException("A digital product requires a download URL");
        }
        // Los productos físicos no exigen unidades al registrarse: la existencia
        // se administra en el dominio de Inventory.
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