package application.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Solicitud de movimiento de inventario (SDD - Inventory Services).
 * variantId es opcional (null para productos sin variante).
 * Para ADJUST (AdjustStock) el campo quantity representa la cantidad
 * verificada final del registro.
 */
@Data
public class StockMovementRequest {
    @NotNull
    private Long warehouseId;
    @NotNull
    private Long productId;
    private Long variantId;
    private Integer quantity;
}

