package application.domain.service;

import application.persistence.mongodb.repository.CartDocumentRepository;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.repository.OrderItemRepository;
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

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryMongoService inventoryMongoService;

    @Autowired
    private CartDocumentRepository cartRepository;

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

    /**
     * Elimina un producto. Se protege la trazabilidad: un producto que ya
     * forma parte de algun pedido no puede borrarse (debe descontinuarse).
     */
    public void deleteById(Long id) {
        ProductEntity product = productRepository.findByIdWithSeller(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + id));
        if (orderItemRepository.existsByProductId(id)) {
            throw new BusinessRuleException(
                    "El producto tiene pedidos asociados: no puede eliminarse, use DiscontinueProduct");
        }
        if (!inventoryMongoService.findByProductId(id).isEmpty()) {
            throw new BusinessRuleException(
                    "El producto tiene registros de inventario: no puede eliminarse, use DiscontinueProduct");
        }
        if (!cartRepository.findByItemsProductId(id).isEmpty()) {
            throw new BusinessRuleException(
                    "El producto esta referenciado en carritos: no puede eliminarse, use DiscontinueProduct");
        }
        productRepository.delete(product);
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
        if (product instanceof application.persistence.mysql.entity.DigitalProductEntity digital
                && (digital.getDownloadUrl() == null || digital.getDownloadUrl().isBlank())) {
            throw new BusinessRuleException(
                    "El producto digital debe definir la direccion de descarga de su archivo");
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
        ProductEntity existing = productRepository.findByIdWithSeller(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + product.getId()));
        if (existing.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("Un producto descontinuado no puede actualizarse");
        }
        validateRequiredData(product);
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setBasePrice(product.getBasePrice());
        existing.setCategory(product.getCategory());
        existing.setBrand(product.getBrand());
        existing.setImages(product.getImages());
        existing.setWeight(product.getWeight());
        existing.setDimensions(product.getDimensions());
        // Los campos propios de un producto digital no viajan por las propiedades
        // comunes de ProductEntity: hay que copiarlos explicitamente o una
        // actualizacion los descartaria en silencio.
        if (existing instanceof application.persistence.mysql.entity.DigitalProductEntity existingDigital
                && product instanceof application.persistence.mysql.entity.DigitalProductEntity updatesDigital) {
            existingDigital.setDownloadUrl(updatesDigital.getDownloadUrl());
            existingDigital.setFileSizeMb(updatesDigital.getFileSizeMb());
            existingDigital.setFileFormat(updatesDigital.getFileFormat());
            existingDigital.setLicenseKeyRequired(updatesDigital.isLicenseKeyRequired());
            existingDigital.setMaxDownloads(updatesDigital.getMaxDownloads());
        }
        return productRepository.save(existing);
    }

    /**
     * PublishProduct: publica un producto suspendido. Debe cumplir los datos
     * obligatorios y no puede estar descontinuado. Publicar no garantiza
     * disponibilidad: para productos fisicos la disponibilidad depende del inventario.
     */
    public ProductEntity publishProduct(Long productId) {
        ProductEntity product = productRepository.findByIdWithSeller(productId)
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
        ProductEntity product = productRepository.findByIdWithSeller(productId)
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
        ProductEntity product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + productId));
        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("El producto ya se encuentra descontinuado");
        }
        product.setStatus(ProductStatus.DISCONTINUED);
        return productRepository.save(product);
    }

    // Variant operations
    public ProductVariantEntity saveVariant(ProductVariantEntity variant) {
        if (variant.getProduct() == null || variant.getProduct().getId() == null) {
            throw new BusinessRuleException("La variante debe estar asociada a un producto valido");
        }

        ProductEntity product = productRepository.findById(variant.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto no encontrado con id: " + variant.getProduct().getId()));
        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException(
                    "No se pueden agregar variantes a un producto descontinuado");
        }

        validateVariantData(variant);
        assertVariantSkuAvailable(variant.getSku(), null);
        variant.setProduct(product);
        return variantRepository.save(variant);
    }

    /**
     * UpdateVariant: actualiza nombre, SKU, precio, stock, atributos y estado de
     * una variante. Solo el vendedor propietario (o un administrador) llega
     * aqui; el servicio comprueba ademas que la variante pertenezca al producto.
     */
    public ProductVariantEntity updateVariant(Long productId, Long variantId,
                                              ProductVariantEntity updates) {
        ProductVariantEntity variant = findVariantInProduct(productId, variantId);
        if (variant.getProduct().getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException(
                    "No se pueden modificar variantes de un producto descontinuado");
        }

        validateVariantData(updates);
        assertVariantSkuAvailable(updates.getSku(), variantId);

        variant.setVariantName(updates.getVariantName());
        variant.setSku(updates.getSku());
        variant.setPrice(updates.getPrice());
        variant.setStock(updates.getStock());
        variant.setAttributes(updates.getAttributes());
        variant.setActive(updates.isActive());
        return variantRepository.save(variant);
    }

    public ProductVariantEntity deactivateVariant(Long productId, Long variantId) {
        ProductVariantEntity variant = findVariantInProduct(productId, variantId);
        if (!variant.isActive()) {
            throw new BusinessRuleException("La variante " + variantId + " ya esta inactiva");
        }
        variant.setActive(false);
        return variantRepository.save(variant);
    }

    public ProductVariantEntity activateVariant(Long productId, Long variantId) {
        ProductVariantEntity variant = findVariantInProduct(productId, variantId);
        if (variant.getProduct().getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException(
                    "No se puede activar una variante de un producto descontinuado");
        }
        if (variant.isActive()) {
            throw new BusinessRuleException("La variante " + variantId + " ya esta activa");
        }
        variant.setActive(true);
        return variantRepository.save(variant);
    }

    private ProductVariantEntity findVariantInProduct(Long productId, Long variantId) {
        ProductVariantEntity variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Variante no encontrada con id: " + variantId));
        if (variant.getProduct() == null || !productId.equals(variant.getProduct().getId())) {
            throw new BusinessRuleException(
                    "La variante " + variantId + " no pertenece al producto " + productId);
        }
        return variant;
    }

    private void validateVariantData(ProductVariantEntity variant) {
        if (variant.getVariantName() == null || variant.getVariantName().isBlank()) {
            throw new BusinessRuleException("El nombre de la variante es obligatorio");
        }
        if (variant.getPrice() == null || variant.getPrice().signum() <= 0) {
            throw new BusinessRuleException("El precio de la variante debe ser mayor que cero");
        }
        if (variant.getStock() != null && variant.getStock() < 0) {
            throw new BusinessRuleException("El stock de la variante no puede ser negativo");
        }
    }

    /**
     * El SKU de variante, cuando se informa, debe ser unico en el catalogo de
     * variantes. En una actualizacion se excluye la propia variante.
     */
    private void assertVariantSkuAvailable(String sku, Long currentVariantId) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        variantRepository.findBySku(sku).ifPresent(existing -> {
            if (currentVariantId == null || !currentVariantId.equals(existing.getId())) {
                throw new BusinessRuleException(
                        "Ya existe una variante con el SKU: " + sku);
            }
        });
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
