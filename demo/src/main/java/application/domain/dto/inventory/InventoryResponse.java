package application.domain.dto.inventory;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryResponse {
    private String id;
    private Long warehouseId;
    private Long productId;
    private Long variantId;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer damagedQuantity;
    private Integer availableQuantity;
    private String status;
    private Boolean lowStock;
}

