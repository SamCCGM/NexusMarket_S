package application.domain.enums;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    DISPATCHED,
    DELIVERED,
    FINALIZED,
    /**
     * Estado terminal para un pedido que no se pago dentro del plazo
     * (expiracion por el sistema) o que el comprador cancela antes de pagar.
     * Complementa el ciclo del SDD: PENDING_PAYMENT -> CANCELLED libera la
     * reserva de inventario hecha por ReserveStock.
     */
    CANCELLED
}