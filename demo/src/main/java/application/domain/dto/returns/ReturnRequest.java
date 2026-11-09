package application.domain.dto.returns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RequestReturn (SDD): solicita una devolucion para una compra realizada.
 * Debe identificar la linea del pedido y describir el motivo.
 */
@Data
public class ReturnRequest {
    @NotNull
    @Positive
    private Long orderId;

    @NotNull
    @Positive
    private Long orderItemId;
    @NotBlank
    @Size(max = 1000)
    private String reason;
    @NotNull
    @Positive
    private Integer quantity;
}
