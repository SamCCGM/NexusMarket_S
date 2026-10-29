package application.service;

import application.domain.enums.InventoryStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mongodb.repository.InventoryDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicios de dominio de Inventory (SDD - Inventory Services).
 *
 * Cada operación corresponde a un movimiento de inventario y actualiza las
 * cantidades de forma consistente para evitar vender las mismas unidades dos
 * veces. Bajo ninguna circunstancia se permiten existencias negativas.
 *
 * Los movimientos definidos por la especificación son:
 *   INCOME      -> AddStock
 *   RESERVATION -> ReserveStock
 *   SALE_EXIT   -> RemoveStock
 *   ADJUSTMENT  -> AdjustStock
 *   RETURN      -> ReturnStock
 */
@Service
public class InventoryMongoService {

    @Autowired
    private InventoryDocumentRepository inventoryRepository;

    // ==================== Domain Services (SDD - Inventory Services) ====================

    /**
     * AddStock (Movimiento: INCOME) - Actor: LogisticsOperator / Seller.
     * Registra el ingreso de unidades a un registro de inventario de una bodega.
     *
     * Reglas de negocio:
     * - La cantidad ingresada debe ser mayor que cero (nunca negativa).
     * - Crea el registro de inventario si aún no existe para esa
     *   bodega/producto/variante.
     */
    public InventoryDocument addStock(Long warehouseId, Long productId, String variantId, Integer quantity) {
        requirePositive(quantity);
        InventoryDocument inventory = findOrCreate(warehouseId, productId, variantId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
        return save(inventory);
    }

    /**
     * ReserveStock (Movimiento: RESERVATION) - Actor: System.
     * Reserva unidades disponibles durante el proceso de compra, después de
     * validar la disponibilidad.
     *
     * Reglas de negocio:
     * - No se puede reservar inventario inexistente.
     * - No se puede reservar inventario marcado como {@code DAMAGED}.
     * - No se puede reservar una cantidad superior a la disponible.
     * - La cantidad disponible nunca resulta negativa.
     * - La disponibilidad debe validarse de nuevo al crear el pedido y antes
     *   de confirmar el pago (agregar al carrito no reserva por sí solo).
     */
    public InventoryDocument reserveStock(Long warehouseId, Long productId, String variantId, Integer quantity) {
        requirePositive(quantity);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);

        if (inventory.getStatus() == InventoryStatus.DAMAGED) {
            throw new BusinessRuleException(
                    "Damaged inventory cannot be reserved (inventory " + inventory.getId() + ")");
        }

        int available = inventory.getAvailableQuantity();
        if (available < quantity) {
            throw new BusinessRuleException(
                    "Insufficient stock. Available: " + available + ", Requested: " + quantity);
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        return save(inventory);
    }

    /**
     * RemoveStock (Movimiento: SALE_EXIT) - Actor: System / LogisticsOperator.
     * Registra la salida de unidades por una venta u otra operación autorizada.
     *
     * Reglas de negocio:
     * - No se puede retirar más de la cantidad total registrada (nunca negativa).
     * - Libera la reserva correspondiente al salir las unidades vendidas.
     */
    public InventoryDocument removeStock(Long warehouseId, Long productId, String variantId, Integer quantity) {
        requirePositive(quantity);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);

        if (inventory.getQuantity() < quantity) {
            throw new BusinessRuleException(
                    "Insufficient stock to remove. Total: " + inventory.getQuantity()
                            + ", Requested: " + quantity);
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - quantity));
        return save(inventory);
    }

    /**
     * AdjustStock (Movimiento: ADJUSTMENT) - Actor: LogisticsOperator.
     * Corrige la cantidad registrada después de una verificación o ajuste
     * autorizado.
     *
     * Reglas de negocio:
     * - La cantidad corregida no puede ser negativa.
     * - Se ajusta a la cantidad verificada (no a un delta).
     * - La reserva se limita al total para no dejar el disponible en negativo.
     */
    public InventoryDocument adjustStock(Long warehouseId, Long productId, String variantId, Integer newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new BusinessRuleException("Adjusted quantity cannot be negative");
        }

        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);
        inventory.setQuantity(newQuantity);
        inventory.setReservedQuantity(Math.min(inventory.getReservedQuantity(), newQuantity));
        return save(inventory);
    }

    /**
     * ReturnStock (Movimiento: RETURN) - Actor: LogisticsOperator.
     * Regresa al inventario de unidades devueltas que puedan volver a estar
     * disponibles.
     *
     * Reglas de negocio:
     * - Solo se suma la cantidad revisada y declarada apta para la venta.
     * - La cantidad debe ser mayor que cero (nunca negativa).
     * - Las unidades devueltas quedan disponibles (no reservadas).
     * - La aprobación de una devolución no implica que las unidades regresen
     *   automáticamente: primero deben recibirse y revisarse.
     */
    public InventoryDocument returnStock(Long warehouseId, Long productId, String variantId, Integer quantity) {
        requirePositive(quantity);
        InventoryDocument inventory = findOrCreate(warehouseId, productId, variantId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
        return save(inventory);
    }

    /**
     * Libera una reserva previa (complemento de ReserveStock), por ejemplo
     * cuando un pedido se cancela o su pago falla.
     */
    public InventoryDocument releaseStock(Long warehouseId, Long productId, String variantId, Integer quantity) {
        requirePositive(quantity);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);
        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - quantity));
        return save(inventory);
    }

    // ==================== Persistence helpers ====================

    public InventoryDocument save(InventoryDocument inventory) {
        inventory.setLastUpdated(LocalDateTime.now());
        return inventoryRepository.save(inventory);
    }

    public Optional<InventoryDocument> findById(String id) {
        return inventoryRepository.findById(id);
    }

    public List<InventoryDocument> findByWarehouseId(Long warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId);
    }

    public List<InventoryDocument> findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public Optional<InventoryDocument> findByWarehouseIdAndProductIdAndVariantId(Long warehouseId, Long productId, String variantId) {
        return inventoryRepository.findByWarehouseIdAndProductIdAndVariantId(warehouseId, productId, variantId);
    }

    public List<InventoryDocument> findByWarehouseIdAndProductId(Long warehouseId, Long productId) {
        return inventoryRepository.findByWarehouseIdAndProductId(warehouseId, productId);
    }

    public List<InventoryDocument> findLowStockItems(Integer threshold) {
        return inventoryRepository.findByQuantityLessThanEqual(threshold);
    }

    public void deleteById(String id) {
        inventoryRepository.deleteById(id);
    }

    // ==================== Internal ====================

    private InventoryDocument findInventoryOrThrow(Long warehouseId, Long productId, String variantId) {
        return inventoryRepository
                .findByWarehouseIdAndProductIdAndVariantId(warehouseId, productId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for warehouse: " + warehouseId + ", product: " + productId));
    }

    private InventoryDocument findOrCreate(Long warehouseId, Long productId, String variantId) {
        return inventoryRepository
                .findByWarehouseIdAndProductIdAndVariantId(warehouseId, productId, variantId)
                .orElseGet(() -> {
                    InventoryDocument created = new InventoryDocument();
                    created.setWarehouseId(warehouseId);
                    created.setProductId(productId);
                    created.setVariantId(variantId);
                    created.setQuantity(0);
                    created.setReservedQuantity(0);
                    created.setStatus(InventoryStatus.AVAILABLE);
                    return created;
                });
    }

    private void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero");
        }
    }
}
