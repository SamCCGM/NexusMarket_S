package application.domain.models;

import application.domain.enums.InventoryStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inventory representa las existencias de un producto físico dentro de una
 * bodega específica.
 *
 * El inventario es distribuido; cada registro debe estar vinculado
 * obligatoriamente a un PhysicalProduct y a una Warehouse. No se permiten
 * existencias negativas y el inventario marcado como DAMAGED no puede
 * reservarse.
 */
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    private int inventoryId;
    private Warehouse warehouse;
    private PhysicalProduct product;
    private int quantity;
    private InventoryStatus status;

}
