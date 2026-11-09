package application.controller.inventory;

import application.domain.dto.inventory.InventoryResponse;
import application.domain.dto.inventory.StockMovementRequest;
import application.persistence.mongodb.document.InventoryDocument;
import application.domain.service.InventoryMongoService;
import application.domain.service.ProductService;
import application.domain.service.SellerService;
import application.domain.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API de Inventory (MongoDB) para los servicios del SDD:
 * AddStock, ReserveStock, RemoveStock, AdjustStock, ReturnStock.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMongoService inventoryService;
    private final ProductService productService;
    private final SellerService sellerService;
    private final WarehouseService warehouseService;

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasRole('SELLER') or hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<InventoryResponse>> getByProduct(@PathVariable Long productId,
                                                               Authentication authentication) {
        assertProductAuthorizedForSeller(productId, authentication);
        return ResponseEntity.ok(inventoryService.findByProductId(productId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasRole('SELLER') or hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<InventoryResponse>> getByWarehouse(@PathVariable Long warehouseId,
                                                                 Authentication authentication) {
        assertWarehouseAuthorizedForSeller(warehouseId, authentication);
        return ResponseEntity.ok(inventoryService.findByWarehouseId(warehouseId).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<InventoryResponse>> getLowStock(
            @RequestParam(required = false, defaultValue = "10") Integer threshold) {
        return ResponseEntity.ok(inventoryService.findLowStockItems(threshold).stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    /**
     * AddStock (INCOME): registra el ingreso de unidades a una bodega.
     * Regla SDD: el ingreso ocurre en una bodega autorizada; un vendedor
     * solo puede operar el inventario de su propia bodega de tipo SELLER.
     */
    @PostMapping("/stock/add")
    @PreAuthorize("hasRole('SELLER') or hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> addStock(@RequestBody @Valid StockMovementRequest request,
                                                      Authentication authentication) {
        assertWarehouseAuthorizedForSeller(request.getWarehouseId(), authentication);
        assertProductAuthorizedForSeller(request.getProductId(), authentication);
        return ResponseEntity.ok(toResponse(
                inventoryService.addStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * Si quien llama es un SELLER, verifica que la bodega sea de tipo SELLER
     * y de su propiedad. Los operadores logisticos y administradores operan
     * cualquier bodega del Marketplace.
     */
    private void assertWarehouseAuthorizedForSeller(Long warehouseId, Authentication authentication) {
        if (!isSellerOnly(authentication)) {
            return;
        }
        Long sellerId = currentSellerId(authentication);
        warehouseService.assertSellerAuthorizedWarehouse(warehouseId, sellerId);
    }

    /**
     * Un vendedor solo puede consultar o ingresar stock de productos propios.
     * Los operadores logisticos y administradores pueden operar sobre todo el
     * catalogo. Se evita que un vendedor use su bodega para alojar inventario
     * de productos ajenos.
     */
    private void assertProductAuthorizedForSeller(Long productId, Authentication authentication) {
        if (!isSellerOnly(authentication)) {
            return;
        }
        Long sellerId = currentSellerId(authentication);
        var product = productService.findById(productId)
                .orElseThrow(() -> new application.exception.ResourceNotFoundException(
                        "Producto no encontrado con id: " + productId));
        if (product.getSeller() == null || !sellerId.equals(product.getSeller().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Un vendedor solo puede operar inventario de sus propios productos (producto "
                            + productId + " no autorizado)");
        }
    }

    private boolean isSellerOnly(Authentication authentication) {
        return authentication.getAuthorities().stream().allMatch(
                a -> a.getAuthority().equals("ROLE_SELLER"));
    }

    private Long currentSellerId(Authentication authentication) {
        return sellerService.findByUserEntityEmail(authentication.getName())
                .map(s -> s.getId())
                .orElseThrow(() -> new application.exception.BusinessRuleException(
                        "El perfil de vendedor no existe para: " + authentication.getName()));
    }

    /**
     * ReserveStock (RESERVATION): reserva unidades disponibles durante el
     * proceso de compra, despues de validar la disponibilidad.
     */
    @PostMapping("/stock/reserve")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> reserveStock(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.reserveStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * RemoveStock (SALE_EXIT): registra la salida de unidades por una venta.
     */
    @PostMapping("/stock/remove")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> removeStock(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.removeStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * AdjustStock (ADJUSTMENT): corrige la cantidad a la verificada.
     */
    @PostMapping("/stock/adjust")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> adjustStock(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.adjustStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * ReturnStock (RETURN): regresa al inventario unidades devueltas aptas.
     */
    @PostMapping("/stock/return")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> returnStock(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.returnStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * MarkDamaged: declara unidades como dañadas/no aptas para la venta.
     * Regla SDD: las unidades dañadas no pueden considerarse disponibles.
     */
    @PostMapping("/stock/damaged")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> markDamaged(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.markDamaged(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    /**
     * ReleaseStock: libera una reserva previa (pago fallido o pedido
     * cancelado, complemento de ReserveStock).
     */
    @PostMapping("/stock/release")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<InventoryResponse> releaseStock(@RequestBody @Valid StockMovementRequest request) {
        return ResponseEntity.ok(toResponse(
                inventoryService.releaseStock(request.getWarehouseId(), request.getProductId(),
                        request.getVariantId(), request.getQuantity())));
    }

    private InventoryResponse toResponse(InventoryDocument doc) {
        return InventoryResponse.builder()
                .id(doc.getId())
                .warehouseId(doc.getWarehouseId())
                .productId(doc.getProductId())
                .variantId(doc.getVariantId())
                .quantity(doc.getQuantity())
                .reservedQuantity(doc.getReservedQuantity())
                .damagedQuantity(doc.getDamagedQuantity())
                .availableQuantity(doc.getAvailableQuantity())
                .status(doc.getStatus() != null ? doc.getStatus().name() : null)
                .lowStock(doc.isLowStock())
                .build();
    }
}
