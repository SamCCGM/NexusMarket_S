package application.domain.models;

import application.domain.enums.WarehouseType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Warehouse representa un espacio físico destinado al almacenamiento y
 * administración de inventario.
 *
 * El sistema distingue entre bodegas del Marketplace (MARKETPLACE) y
 * bodegas de vendedores (SELLER). El inventario se administra mediante
 * los registros Inventory, por lo que no se almacena una lista aquí.
 */
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {

    private int warehouseId;
    private String location;
    private WarehouseType type;

}
