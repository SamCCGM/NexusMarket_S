package application.persistence.mongodb.document;

import application.domain.enums.InventoryStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "inventories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "warehouse_product_variant_idx", def = "{'warehouseId': 1, 'productId': 1, 'variantId': 1}", unique = true)
public class InventoryDocument {

    @Id
    private String id;

    @Indexed
    private Long warehouseId;

    @Indexed
    private Long productId;

    private String variantId;

    private Integer quantity = 0;
    private Integer reservedQuantity = 0;
    private Integer minStockLevel = 10;
    private Integer maxStockLevel = 1000;

    /**
     * Estado operativo del inventario. El estado {@code DAMAGED} identifica
     * existencias que no pueden reservarse (regla del dominio Inventory).
     */
    private InventoryStatus status = InventoryStatus.AVAILABLE;

    private LocalDateTime lastUpdated;
    private String lastUpdatedBy;

    public Integer getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= minStockLevel;
    }
}