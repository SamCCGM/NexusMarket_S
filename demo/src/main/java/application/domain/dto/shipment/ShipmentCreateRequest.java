package application.domain.dto.shipment;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CreateShipment (SDD): datos opcionales al crear el envio de un pedido
 * fisico pagado. El destino tomado es la direccion de envio del pedido.
 */
@Data
public class ShipmentCreateRequest {
    @Positive
    private Long logisticsOperatorId;

    @Positive
    private Long warehouseId;

    @Size(max = 150)
    private String carrier;

    @PositiveOrZero
    private Integer estimatedDays;
}
