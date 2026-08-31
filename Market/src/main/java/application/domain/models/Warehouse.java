package application.domain.models;

import application.domain.enums.WarehouseType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Warehouse {

    private Integer warehouseId;
    private String location;
    private WarehouseType type;
    private Inventory inventory;
}