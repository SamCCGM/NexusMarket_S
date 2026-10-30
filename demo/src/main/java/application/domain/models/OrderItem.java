package application.domain.models;

import application.domain.valueObjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OrderItem representa una linea de un pedido confirmado.
 * Conserva el precio unitario aplicado al confirmar la compra como parte
 * del historial; los importes deben corresponder al pedido.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    private int orderItemId;
    private Product product;
    private int quantity;
    private Money unitPrice;

    /**
     * Reglas de una linea de pedido: producto comprable, cantidad positiva
     * y precio mayor que cero (registra el precio del sistema, no el del cliente).
     */
    public void assertValidLine() {
        if (product == null) {
            throw new application.exception.BusinessRuleException(
                    "La linea del pedido debe referenciar un producto del pedido");
        }
        if (quantity <= 0) {
            throw new application.exception.BusinessRuleException(
                    "No se permiten cantidades invalidas en el pedido");
        }
        if (unitPrice == null || !unitPrice.isPositive()) {
            throw new application.exception.BusinessRuleException(
                    "El precio unitario registrado debe ser mayor que cero");
        }
    }

    /** Importe de la linea segun el precio historico conservado. */
    public Money lineTotal() {
        assertValidLine();
        return unitPrice.multiply(quantity);
    }

    /** Los productos fisicos requieren despacho; los digitales no. */
    public boolean isPhysicalPurchase() {
        return product instanceof PhysicalProduct
                || (product != null && product.getClass().getName()
                        .equals("application.domain.models.PhysicalProduct"));
    }
}
