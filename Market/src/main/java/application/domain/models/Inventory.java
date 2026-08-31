package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    private int inventoryId;
    private Warehouse warehouse;
    private Product product;
    private int quantity;
    
}