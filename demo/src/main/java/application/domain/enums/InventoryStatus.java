package application.domain.enums;

/**
 * Estado operativo de un registro de inventario.
 * El inventario marcado como {@code DAMAGED} no puede reservarse.
 */
public enum InventoryStatus {
    AVAILABLE,
    RESERVED,
    DAMAGED
}
