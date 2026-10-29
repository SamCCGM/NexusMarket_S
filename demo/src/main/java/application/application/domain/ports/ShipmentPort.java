package application.domain.ports;

import application.domain.enums.ShipmentStatus;
import application.domain.valueobjects.Address;

/**
 * Output Port para los servicios externos de despacho y seguimiento de envíos.
 * Separa la lógica del Marketplace de los proveedores externos de transporte.
 */
public interface ShipmentPort {

    /**
     * Solicita la creación de un envío para un pedido físico pagado.
     *
     * @return identificador del envío creado.
     */
    Long createShipment(Long orderId, Address destination);

    /**
     * Obtiene el estado del proceso de entrega de un envío.
     */
    ShipmentStatus getShipmentStatus(Long shipmentId);

}
