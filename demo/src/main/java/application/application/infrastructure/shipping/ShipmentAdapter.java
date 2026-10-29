package application.application.infrastructure.shipping;

import application.application.domain.enums.ShipmentStatus;
import application.application.domain.ports.ShipmentPort;
import application.application.domain.valueObjects.Address;
import application.application.exception.BusinessRuleException;
import application.application.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación concreta (adaptador) del Output Port {@link ShipmentPort}.
 *
 * Pertenece a la capa externa: el dominio solo conoce la interfaz. En una
 * integración real, aquí se comunicaría la creación y el seguimiento del envío
 * con el proveedor logístico correspondiente.
 *
 * En esta primera entrega el adaptador simula la creación del envío y conserva
 * su estado en memoria.
 */
@Slf4j
@Component
public class ShipmentAdapter implements ShipmentPort {

    private final Map<Long, ShipmentStatus> shipments = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Long createShipment(Long orderId, Address destination) {
        if (orderId == null) {
            throw new BusinessRuleException("Order id is required to create a shipment");
        }
        if (destination == null || destination.getValue() == null || destination.getValue().isBlank()) {
            throw new BusinessRuleException("A valid destination is required to create a shipment");
        }

        long shipmentId = sequence.getAndIncrement();
        shipments.put(shipmentId, ShipmentStatus.CREATED);

        log.info("[SHIPMENT] created shipmentId={} orderId={} destination={}",
                shipmentId, orderId, destination.getValue());
        return shipmentId;
    }

    @Override
    public ShipmentStatus getShipmentStatus(Long shipmentId) {
        ShipmentStatus status = shipmentId == null ? null : shipments.get(shipmentId);
        if (status == null) {
            throw new ResourceNotFoundException("Shipment not found: " + shipmentId);
        }
        return status;
    }
}
