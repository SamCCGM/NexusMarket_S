package application.domain.models;

import application.domain.valueObjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Invoice representa la informacion comercial asociada a una venta. Se genera
 * como consecuencia de un pedido con pago confirmado y mantiene la
 * trazabilidad; su informacion corresponde a los importes del pedido.
 */
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    private int invoiceId;
    private String invoiceNumber;
    private Order order;
    private Money total;

    /**
     * La factura no se genera como si el pago hubiera sido exitoso cuando el
     * pago esta pendiente o fallido.
     */
    public void assertIssuedAgainstPaidOrder() {
        if (order == null) {
            throw new application.exception.BusinessRuleException(
                    "La facturacion debe estar asociada a un pedido valido");
        }
        if (order.getStatus() == null || !order.isPaymentConfirmed()) {
            throw new application.exception.BusinessRuleException(
                    "No se genera factura para un pedido con pago pendiente o fallido");
        }
    }
}
