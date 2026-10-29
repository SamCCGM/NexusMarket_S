package application.domain.models;

import application.domain.enums.ShipmentStatus;
import application.domain.valueobjects.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shipment representa el proceso logístico utilizado para transportar
 * productos físicos desde una bodega hasta el destinatario.
 *
 * Los productos digitales no requieren un Shipment, ya que su entrega se
 * realiza de forma inmediata después de la confirmación del pago.
 */
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    private Integer shipmentId;
    private Order order;
    private Address destination;
    private ShipmentStatus status;

}
