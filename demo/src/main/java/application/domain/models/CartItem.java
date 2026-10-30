package application.domain.models;

import application.domain.valueObjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CartItem representa una linea del carrito: un producto y una cantidad.
 * El carrito no conserva un precio historico; el cobro se calcula al crear
 * el pedido para que los importes correspondan siempre al sistema.
 */
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    private int cartItemId;
    private Product product;
    private int quantity;

    /** Reglas de una linea del carrito: producto valido y cantidad positiva. */
    public void assertValidItem() {
        if (product == null) {
            throw new application.exception.BusinessRuleException(
                    "Cada linea del carrito debe referenciar un producto existente");
        }
        if (quantity <= 0) {
            throw new application.exception.BusinessRuleException(
                    "La cantidad solicitada debe ser mayor que cero");
        }
        if (product.getStatus() != null && !product.canBePurchased()) {
            throw new application.exception.BusinessRuleException(
                    "Un producto suspendido o descontinuado no puede incluirse en nuevas compras");
        }
    }
}
