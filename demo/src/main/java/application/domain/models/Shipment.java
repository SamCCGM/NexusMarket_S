package application.domain.models;

import application.domain.enums.ShipmentStatus;
import application.domain.valueObjects.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Shipment transporta productos fisicos desde una bodega hasta el destinatario.
 * Los productos digitales no requieren Shipment. La entrega debe confirmarse
 * antes de finalizar el pedido.
 */
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    private Integer shipmentId;
    private Order order;
    private Address destination;
    private ShipmentStatus status;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;

    /**
     * El envio se crea para un pedido fisico pagado, con destino valido,
     * y no se duplican envios en curso para el mismo pedido.
     */
    public void assertCanBeCreated() {
        if (order == null) {
            throw new application.exception.BusinessRuleException(
                    "El envio debe asociarse a un pedido valido");
        }
        if (!order.requiresPhysicalDispatch()) {
            throw new application.exception.BusinessRuleException(
                    "Solo los pedidos con productos fisicos requieren envio");
        }
        if (!order.isPaymentConfirmed()) {
            throw new application.exception.BusinessRuleException(
                    "Solo se crea envio para un pedido cuyo pago fue confirmado");
        }
        if (destination == null || !destination.hasText()) {
            throw new application.exception.BusinessRuleException(
                    "El envio debe tener un destino valido");
        }
        status = ShipmentStatus.CREATED;
    }

    /** DispatchShipment: solo envios creados pueden despacharse, una sola vez. */
    public void dispatch() {
        if (status != ShipmentStatus.CREATED) {
            throw new application.exception.BusinessRuleException(
                    "Solo un envio CREATED puede despacharse (estado actual: " + status + ")");
        }
        status = ShipmentStatus.DISPATCHED;
    }

    /** Se traslada hasta que la entrega se confirma. */
    public void markInTransit() {
        if (status != ShipmentStatus.DISPATCHED) {
            throw new application.exception.BusinessRuleException(
                    "Solo un envio DISPATCHED puede pasar a IN_TRANSIT");
        }
        status = ShipmentStatus.IN_TRANSIT;
    }

    /** ConfirmDelivery: registra la entrega en el destino. */
    public void confirmDelivery() {
        if (status != ShipmentStatus.DISPATCHED && status != ShipmentStatus.IN_TRANSIT) {
            throw new application.exception.BusinessRuleException(
                    "Solo un envio despachado puede confirmar su entrega");
        }
        status = ShipmentStatus.DELIVERED;
        actualDeliveryDate = LocalDateTime.now();
    }
}
