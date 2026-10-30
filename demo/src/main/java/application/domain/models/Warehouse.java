package application.domain.models;

import application.domain.enums.WarehouseType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Warehouse representa el espacio fisico de almacenamiento. El inventario se
 * administra mediante registros Inventory; una bodega de tipo SELLER pertenece
 * a un vendedor del Marketplace.
 */
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {

    private int warehouseId;
    private String name;
    private String location;
    private WarehouseType type;
    private Seller seller;

    /**
     * Reglas de registro: ubicacion valida y tipo de bodega valido; una bodega
     * de tipo SELLER debe estar asociada a un vendedor.
     */
    public void assertValidRegistration() {
        if (name == null || name.isBlank()) {
            throw new application.exception.BusinessRuleException("El nombre de la bodega es obligatorio");
        }
        if (location == null || location.isBlank()) {
            throw new application.exception.BusinessRuleException(
                    "La bodega debe tener una ubicacion valida (direccion obligatoria)");
        }
        if (type == null) {
            throw new application.exception.BusinessRuleException(
                    "El tipo de bodega debe ser MARKETPLACE o SELLER");
        }
        if (type == WarehouseType.SELLER && seller == null) {
            throw new application.exception.BusinessRuleException(
                    "Una bodega de tipo SELLER debe estar asociada a un vendedor");
        }
    }

    public boolean isMarketplaceWarehouse() {
        return type == WarehouseType.MARKETPLACE;
    }
}
