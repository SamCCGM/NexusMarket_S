package application.domain.models;

import application.domain.valueobjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OrderItem representa una línea individual dentro de un pedido confirmado.
 *
 * A diferencia de CartItem, un OrderItem forma parte de una compra
 * formalizada y conserva el precio unitario aplicado al confirmar la compra
 * como parte del historial.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    private int orderItemId;
    private Product product;
    private int quantity;
    private Money price;

}
