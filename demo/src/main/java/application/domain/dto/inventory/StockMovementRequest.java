package application.domain.dto.inventory;

import lombok.Data;

/**
 * Solicitud de movimiento de inventario (SDD - Inventory Services).
 * variantId es opcional (null para productos sin variante).
 * Para ADJUST (AdjustStock) el campo quantity representa la cantidad
 * verificada final del registro.
 */
@Data
public class StockMovementRequest {
    private Long warehouseId;
    private Long productId;
    private String variantId;
    private Integer quantity;
}
