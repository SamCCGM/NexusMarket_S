package application.domain.enums;

/**
 * Movimientos de inventario que modifican las existencias disponibles.
 * Son operaciones del dominio de Inventory, no entidades independientes.
 */
public enum InventoryMovement {
    INCOME,
    RESERVATION,
    SALE_EXIT,
    ADJUSTMENT,
    RETURN
}
