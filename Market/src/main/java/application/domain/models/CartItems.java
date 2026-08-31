package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    private int cartItemId;
    private Product product;
    private int quantity;
    
}