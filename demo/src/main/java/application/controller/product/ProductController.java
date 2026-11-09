package application.controller.product;

import application.domain.dto.product.ProductRequest;
import application.domain.dto.product.ProductResponse;
import application.domain.dto.product.ProductVariantRequest;
import application.domain.dto.product.ProductVariantResponse;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.entity.SellerEntity;
import application.domain.service.ProductService;
import application.domain.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final SellerService sellerService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        
        Page<ProductEntity> products;
        if (keyword != null && !keyword.isEmpty()) {
            List<ProductEntity> searchResults = productService.searchByKeyword(keyword);
            // Convert to page manually for search
            products = new org.springframework.data.domain.PageImpl<>(searchResults, pageable, searchResults.size());
        } else if (status != null) {
            products = productService.findByStatus(
                    application.domain.enums.ProductStatus.valueOf(status.toUpperCase()), pageable);
        } else if (category != null) {
            List<ProductEntity> categoryProducts = productService.findByCategory(category);
            products = new org.springframework.data.domain.PageImpl<>(categoryProducts, pageable, categoryProducts.size());
        } else {
            products = productService.findByStatus(application.domain.enums.ProductStatus.PUBLISHED, pageable);
        }

        return ResponseEntity.ok(products.map(this::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<ProductResponse>> getProductsBySeller(@PathVariable Long sellerId) {
        List<ProductEntity> products = productService.findBySellerId(sellerId);
        return ResponseEntity.ok(products.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/my-products")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<ProductResponse>> getMyProducts(Authentication authentication) {
        List<ProductEntity> products;
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) {
            // El administrador consulta todos los productos; no tiene perfil de vendedor.
            products = productService.findAllProducts();
        } else {
            String email = authentication.getName();
            SellerEntity seller = sellerService.findByUserEntityEmail(email)
                    .orElseThrow(() -> new AccessDeniedException(
                            "El usuario autenticado no tiene perfil de vendedor: " + email));
            products = productService.findBySellerId(seller.getId());
        }
        return ResponseEntity.ok(products.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody @Valid ProductRequest request,
            Authentication authentication) {
        
        String email = authentication.getName();
        SellerEntity seller = sellerService.findByUserEntityEmail(email).orElse(null);
        if (seller == null) {
            // El administrador no tiene perfil de vendedor: no puede registrar
            // productos a nombre propio. Antes esto terminaba en un error 500.
            return ResponseEntity.status(403).build();
        }

        ProductEntity product = toEntity(request, seller);
        ProductEntity saved = productService.registerProduct(product);
        
        if (request.getVariants() != null) {
            for (ProductVariantRequest variantReq : request.getVariants()) {
                ProductVariantEntity variant = toVariantEntity(variantReq, saved);
                productService.saveVariant(variant);
            }
        }

        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestBody @Valid ProductRequest request,
            Authentication authentication) {
        
        return productService.findById(id)
                .map(existing -> {
                    // Autorizacion: el administrador puede actuar sobre cualquier
                    // producto sin requerir un perfil de vendedor propio.
                    if (!isOwnerOrAdmin(existing, authentication)) {
                        return ResponseEntity.status(403).<ProductResponse>build();
                    }

                    updateEntity(existing, request);
                    ProductEntity updated = productService.updateProduct(existing);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, Authentication authentication) {
        return productService.findById(id)
                .map(existing -> {
                    // Autorizacion: el administrador puede actuar sobre cualquier
                    // producto sin requerir un perfil de vendedor propio.
                    if (!isOwnerOrAdmin(existing, authentication)) {
                        return ResponseEntity.status(403).<Void>build();
                    }

                    productService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Product lifecycle endpoints (SDD: Publish / Suspend / Discontinue)
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductResponse> publishProduct(@PathVariable Long id, Authentication authentication) {
        return productService.findById(id)
                .map(existing -> {
                    if (!isOwnerOrAdmin(existing, authentication)) {
                        return ResponseEntity.status(403).<ProductResponse>build();
                    }
                    return ResponseEntity.ok(toResponse(productService.publishProduct(id)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductResponse> suspendProduct(@PathVariable Long id, Authentication authentication) {
        return productService.findById(id)
                .map(existing -> {
                    if (!isOwnerOrAdmin(existing, authentication)) {
                        return ResponseEntity.status(403).<ProductResponse>build();
                    }
                    return ResponseEntity.ok(toResponse(productService.suspendProduct(id)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/discontinue")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductResponse> discontinueProduct(@PathVariable Long id, Authentication authentication) {
        return productService.findById(id)
                .map(existing -> {
                    if (!isOwnerOrAdmin(existing, authentication)) {
                        return ResponseEntity.status(403).<ProductResponse>build();
                    }
                    return ResponseEntity.ok(toResponse(productService.discontinueProduct(id)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Autorización para operar sobre un producto: el administrador puede actuar
     * sobre cualquier producto; un vendedor solo sobre los suyos.
     */
    private boolean isOwnerOrAdmin(ProductEntity product, Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) {
            return true;
        }
        String email = authentication.getName();
        SellerEntity seller = sellerService.findByUserEntityEmail(email)
                .orElseThrow(() -> new AccessDeniedException(
                        "El usuario autenticado no tiene perfil de vendedor: " + email));
        return product.getSeller() != null && product.getSeller().getId().equals(seller.getId());
    }

    // Variant endpoints
    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductVariantResponse> addVariant(
            @PathVariable Long productId,
            @RequestBody @Valid ProductVariantRequest request,
            Authentication authentication) {
        
        return productService.findById(productId)
                .map(product -> {
                    // Solo el vendedor dueno del producto (o un administrador)
                    // puede anadir variantes; antes cualquier vendedor podia
                    // modificar el catalogo de otro.
                    if (!isOwnerOrAdmin(product, authentication)) {
                        return ResponseEntity.status(403).<ProductVariantResponse>build();
                    }
                    ProductVariantEntity variant = toVariantEntity(request, product);
                    ProductVariantEntity saved = productService.saveVariant(variant);
                    return ResponseEntity.ok(toVariantResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductVariantResponse> updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @RequestBody @Valid ProductVariantRequest request,
            Authentication authentication) {

        return productService.findById(productId)
                .map(product -> {
                    if (!isOwnerOrAdmin(product, authentication)) {
                        return ResponseEntity.status(403).<ProductVariantResponse>build();
                    }
                    ProductVariantEntity updates = toVariantEntity(request, product);
                    ProductVariantEntity updated = productService.updateVariant(
                            productId, variantId, updates);
                    return ResponseEntity.ok(toVariantResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{productId}/variants/{variantId}/deactivate")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductVariantResponse> deactivateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            Authentication authentication) {

        return productService.findById(productId)
                .map(product -> {
                    if (!isOwnerOrAdmin(product, authentication)) {
                        return ResponseEntity.status(403).<ProductVariantResponse>build();
                    }
                    return ResponseEntity.ok(toVariantResponse(
                            productService.deactivateVariant(productId, variantId)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{productId}/variants/{variantId}/activate")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<ProductVariantResponse> activateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            Authentication authentication) {

        return productService.findById(productId)
                .map(product -> {
                    if (!isOwnerOrAdmin(product, authentication)) {
                        return ResponseEntity.status(403).<ProductVariantResponse>build();
                    }
                    return ResponseEntity.ok(toVariantResponse(
                            productService.activateVariant(productId, variantId)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{productId}/variants")
    public ResponseEntity<List<ProductVariantResponse>> getVariants(@PathVariable Long productId) {
        List<ProductVariantEntity> variants = productService.findVariantsByProductId(productId);
        return ResponseEntity.ok(variants.stream().map(this::toVariantResponse).collect(Collectors.toList()));
    }

    private ProductResponse toResponse(ProductEntity entity) {
        ProductResponse.ProductResponseBuilder builder = ProductResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .basePrice(entity.getBasePrice())
                .sku(entity.getSku())
                .status(entity.getStatus().name())
                .category(entity.getCategory())
                .brand(entity.getBrand())
                .images(entity.getImages())
                .weight(entity.getWeight())
                .dimensions(entity.getDimensions())
                .sellerId(entity.getSeller().getId())
                .sellerName(entity.getSeller().getCompanyName())
                .variants(entity.getVariants() != null ?
                        entity.getVariants().stream().map(this::toVariantResponse).collect(Collectors.toList()) : null)
                .productType(entity.getClass().getSimpleName().replace("Entity", ""));

        if (entity instanceof application.persistence.mysql.entity.DigitalProductEntity digital) {
            builder.downloadUrl(digital.getDownloadUrl())
                    .fileSizeMb(digital.getFileSizeMb())
                    .fileFormat(digital.getFileFormat())
                    .licenseKeyRequired(digital.isLicenseKeyRequired())
                    .maxDownloads(digital.getMaxDownloads());
        }
        return builder.build();
    }

    private ProductVariantResponse toVariantResponse(ProductVariantEntity entity) {
        return ProductVariantResponse.builder()
                .id(entity.getId())
                .variantName(entity.getVariantName())
                .sku(entity.getSku())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .attributes(entity.getAttributes())
                .active(entity.isActive())
                .build();
    }

    private ProductEntity toEntity(ProductRequest request, SellerEntity seller) {
        boolean digital = request.getProductType() != null
                && "DIGITAL".equalsIgnoreCase(request.getProductType());

        ProductEntity product = digital
                ? new application.persistence.mysql.entity.DigitalProductEntity()
                : new application.persistence.mysql.entity.PhysicalProductEntity();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setSku(request.getSku());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setImages(request.getImages());
        product.setSeller(seller);

        if (digital) {
            applyDigitalFields((application.persistence.mysql.entity.DigitalProductEntity) product, request);
        } else {
            product.setWeight(request.getWeight());
            product.setDimensions(request.getDimensions());
        }
        return product;
    }

    private void applyDigitalFields(application.persistence.mysql.entity.DigitalProductEntity digital,
                                    ProductRequest request) {
        digital.setDownloadUrl(request.getDownloadUrl());
        digital.setFileSizeMb(request.getFileSizeMb());
        digital.setFileFormat(request.getFileFormat());
        if (request.getLicenseKeyRequired() != null) {
            digital.setLicenseKeyRequired(request.getLicenseKeyRequired());
        }
        if (request.getMaxDownloads() != null) {
            digital.setMaxDownloads(request.getMaxDownloads());
        }
    }

    private void updateEntity(ProductEntity entity, ProductRequest request) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setBasePrice(request.getBasePrice());
        entity.setCategory(request.getCategory());
        entity.setBrand(request.getBrand());
        entity.setImages(request.getImages());
        if (entity instanceof application.persistence.mysql.entity.DigitalProductEntity digital) {
            // En una actualizacion parcial se conservan los datos digitales que
            // no vengan en la peticion.
            if (request.getDownloadUrl() != null) {
                digital.setDownloadUrl(request.getDownloadUrl());
            }
            if (request.getFileSizeMb() != null) {
                digital.setFileSizeMb(request.getFileSizeMb());
            }
            if (request.getFileFormat() != null) {
                digital.setFileFormat(request.getFileFormat());
            }
            if (request.getLicenseKeyRequired() != null) {
                digital.setLicenseKeyRequired(request.getLicenseKeyRequired());
            }
            if (request.getMaxDownloads() != null) {
                digital.setMaxDownloads(request.getMaxDownloads());
            }
        } else {
            entity.setWeight(request.getWeight());
            entity.setDimensions(request.getDimensions());
        }
    }

    private ProductVariantEntity toVariantEntity(ProductVariantRequest request, ProductEntity product) {
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProduct(product);
        variant.setVariantName(request.getVariantName());
        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        variant.setStock(request.getStock());
        variant.setAttributes(request.getAttributes());
        variant.setActive(request.isActive());
        return variant;
    }
}