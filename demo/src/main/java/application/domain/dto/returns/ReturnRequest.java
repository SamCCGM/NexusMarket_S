package application.domain.dto.returns;

import lombok.Data;

/**
 * RequestReturn (SDD): solicita una devolucion para una compra realizada.
 * Debe identificar la linea del pedido y describir el motivo.
 */
@Data
public class ReturnRequest {
    private Long orderId;
    private Long orderItemId;
    private String reason;
    private Integer quantity;
}
