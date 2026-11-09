package application.domain.service;

import application.domain.enums.InventoryStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mongodb.repository.InventoryDocumentRepository;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.entity.WarehouseEntity;
import application.persistence.mysql.repository.ProductRepository;
import application.persistence.mysql.repository.ProductVariantRepository;
import application.persistence.mysql.repository.WarehouseRepository;
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

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

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
    public InventoryDocument addStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        WarehouseEntity warehouse = validateTarget(warehouseId, productId, variantId);
        assertCapacity(warehouse, quantity);
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
    public InventoryDocument reserveStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        validateTarget(warehouseId, productId, variantId);
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
    public InventoryDocument removeStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        // Una salida ya comprometida (pedido pagado) debe poder completarse
        // aunque la bodega se haya desactivado despues.
        validateTarget(warehouseId, productId, variantId, false);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);

        int sellable = inventory.getQuantity() - (inventory.getDamagedQuantity() == null ? 0 : inventory.getDamagedQuantity());
        if (sellable < quantity) {
            throw new BusinessRuleException(
                    "Insufficient sellable stock to remove. Sellable: " + sellable
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
    public InventoryDocument adjustStock(Long warehouseId, Long productId, Long variantId, Integer newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new BusinessRuleException("Adjusted quantity cannot be negative");
        }

        WarehouseEntity warehouse = validateTarget(warehouseId, productId, variantId);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);
        assertCapacityForAdjustment(warehouse, inventory, newQuantity);

        int reserved = zeroIfNull(inventory.getReservedQuantity());
        int damaged = zeroIfNull(inventory.getDamagedQuantity());

        // Un ajuste no puede dejar por debajo de la realidad ya comprometida:
        // las reservas pertenecen a pedidos existentes y las unidades danadas
        // siguen formando parte del total fisico. Antes se recortaban en
        // silencio, rompiendo la trazabilidad de pedidos y devoluciones.
        if (newQuantity < reserved) {
            throw new BusinessRuleException(
                    "No se puede ajustar el inventario por debajo de las unidades reservadas ("
                            + reserved + "). Cancele o complete los pedidos asociados primero");
        }
        if (newQuantity < reserved + damaged) {
            throw new BusinessRuleException(
                    "No se puede ajustar el inventario por debajo de las unidades reservadas y danadas ("
                            + (reserved + damaged) + ")");
        }

        inventory.setQuantity(newQuantity);
        return save(inventory);
    }

    /**
     * MarkDamaged (Actor: LogisticsOperator).
     * Declara unidades como dañadas/no aptas para la venta sin retirarlas del
     * registro: siguen en el total, pero dejan de estar disponibles.
     *
     * Reglas de negocio (SDD): las unidades dañadas no pueden considerarse
     * disponibles ni reservarse; la cantidad dañada nunca puede superar las
     * existencias no reservadas.
     */
    public InventoryDocument markDamaged(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        validateTarget(warehouseId, productId, variantId);
        InventoryDocument inventory = findInventoryOrThrow(warehouseId, productId, variantId);

        if (inventory.getAvailableQuantity() < quantity) {
            throw new BusinessRuleException(
                    "No se pueden declarar mas unidades danadas que las disponibles (disponible: "
                            + inventory.getAvailableQuantity() + ")");
        }

        inventory.setDamagedQuantity(
                (inventory.getDamagedQuantity() == null ? 0 : inventory.getDamagedQuantity()) + quantity);
        return save(inventory);
    }

    /**
     * ReturnStock dañado (RETURN no apto): reincorpora unidades recibidas que
     * NO pueden volver a venderse; se suman al total y a las dañadas.
     */
    public InventoryDocument returnDamagedStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        WarehouseEntity warehouse = validateTarget(warehouseId, productId, variantId);
        assertCapacity(warehouse, quantity);
        InventoryDocument inventory = findOrCreate(warehouseId, productId, variantId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventory.setDamagedQuantity(
                (inventory.getDamagedQuantity() == null ? 0 : inventory.getDamagedQuantity()) + quantity);
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
    public InventoryDocument returnStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        WarehouseEntity warehouse = validateTarget(warehouseId, productId, variantId);
        assertCapacity(warehouse, quantity);
        InventoryDocument inventory = findOrCreate(warehouseId, productId, variantId);
        inventory.setQuantity(inventory.getQuantity() + quantity);
        return save(inventory);
    }

    /**
     * Libera una reserva previa (complemento de ReserveStock), por ejemplo
     * cuando un pedido se cancela o su pago falla.
     */
    public InventoryDocument releaseStock(Long warehouseId, Long productId, Long variantId, Integer quantity) {
        requirePositive(quantity);
        // Liberar una reserva existente no crea stock nuevo: debe funcionar
        // incluso si la bodega o la variante ya no estan operables.
        validateTarget(warehouseId, productId, variantId, false);
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

    public List<InventoryDocument> findByAll() {
        return inventoryRepository.findAll();
    }

    public List<InventoryDocument> findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public Optional<InventoryDocument> findByWarehouseIdAndProductIdAndVariantId(Long warehouseId, Long productId, Long variantId) {
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

    /**
     * Valida que la bodega, el producto y la variante existan y sean operables.
     * Evita crear registros de inventario huerfanos con IDs inexistentes y que
     * una variante de otro producto se use para mover stock.
     */
    private WarehouseEntity validateTarget(Long warehouseId, Long productId, Long variantId) {
        return validateTarget(warehouseId, productId, variantId, true);
    }

    /**
     * @param requireOperable true para movimientos que crean stock o reservas
     *                        nuevas; false para liberar reservas o consumir
     *                        stock de operaciones ya comprometidas.
     */
    private WarehouseEntity validateTarget(Long warehouseId, Long productId, Long variantId,
                                           boolean requireOperable) {
        if (warehouseId == null || productId == null) {
            throw new BusinessRuleException("Warehouse id and product id are required");
        }

        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));
        if (requireOperable && !warehouse.isActive()) {
            throw new BusinessRuleException(
                    "La bodega " + warehouseId + " esta inactiva y no admite movimientos de inventario");
        }

        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (variantId != null) {
            ProductVariantEntity variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
            if (variant.getProduct() == null
                    || !productId.equals(variant.getProduct().getId())) {
                throw new BusinessRuleException(
                        "La variante " + variantId + " no pertenece al producto " + productId);
            }
            if (requireOperable && !variant.isActive()) {
                throw new BusinessRuleException("La variante " + variantId + " no esta activa");
            }
        }

        return warehouse;
    }

    /**
     * Regla de capacidad: la suma de existencias de la bodega no puede superar
     * su capacidad configurada. Una capacidad nula o no positiva se interpreta
     * como bodega sin limite.
     */
    private void assertCapacity(WarehouseEntity warehouse, int additionalQuantity) {
        if (warehouse.getCapacity() == null || warehouse.getCapacity() <= 0) {
            return;
        }
        int current = inventoryRepository.findByWarehouseId(warehouse.getId()).stream()
                .mapToInt(i -> zeroIfNull(i.getQuantity()))
                .sum();
        if (current + additionalQuantity > warehouse.getCapacity()) {
            throw new BusinessRuleException(
                    "La bodega " + warehouse.getId() + " superaria su capacidad ("
                            + warehouse.getCapacity() + "). Ocupado: " + current
                            + ", solicitado: " + additionalQuantity);
        }
    }

    private void assertCapacityForAdjustment(WarehouseEntity warehouse,
                                             InventoryDocument inventory,
                                             int newQuantity) {
        if (warehouse.getCapacity() == null || warehouse.getCapacity() <= 0) {
            return;
        }
        int totalWithoutCurrent = inventoryRepository.findByWarehouseId(warehouse.getId()).stream()
                .filter(i -> !java.util.Objects.equals(i.getId(), inventory.getId()))
                .mapToInt(i -> zeroIfNull(i.getQuantity()))
                .sum();
        if (totalWithoutCurrent + newQuantity > warehouse.getCapacity()) {
            throw new BusinessRuleException(
                    "El ajuste superaria la capacidad de la bodega " + warehouse.getId()
                            + " (" + warehouse.getCapacity() + ")");
        }
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private InventoryDocument findInventoryOrThrow(Long warehouseId, Long productId, Long variantId) {
        return inventoryRepository
                .findByWarehouseIdAndProductIdAndVariantId(warehouseId, productId, variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for warehouse: " + warehouseId + ", product: " + productId));
    }

    private InventoryDocument findOrCreate(Long warehouseId, Long productId, Long variantId) {
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

