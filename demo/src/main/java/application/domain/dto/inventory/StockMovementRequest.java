package application.domain.dto.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
    @Positive
    private Long warehouseId;

    @NotNull
    @Positive
    private Long productId;

    @Positive
    private Long variantId;

    @NotNull
    @PositiveOrZero
    private Integer quantity;
}

