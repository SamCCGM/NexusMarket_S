package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Invoice representa la información comercial asociada a una venta
 * realizada dentro del Marketplace.
 *
 * La factura se genera como consecuencia de un pedido y mantiene la
 * trazabilidad de la venta correspondiente.
 */
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    private int invoiceId;
    private Order order;

}
