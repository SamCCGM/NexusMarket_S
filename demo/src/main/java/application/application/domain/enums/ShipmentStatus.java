package application.domain.enums;

/**
 * Estado del proceso logístico de un envío.
 * Los productos digitales no requieren envío físico.
 */
public enum ShipmentStatus {
    CREATED,
    DISPATCHED,
    IN_TRANSIT,
    DELIVERED
}
