package application.domain.dto.shipment;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * CreateShipment (SDD): datos opcionales al crear el envio de un pedido
 * fisico pagado. El destino tomado es la direccion de envio del pedido.
 */
@Data
public class ShipmentCreateRequest {
    private Long logisticsOperatorId;
    private Long warehouseId;
    private String carrier;
    @PositiveOrZero
    private Integer estimatedDays;
}
