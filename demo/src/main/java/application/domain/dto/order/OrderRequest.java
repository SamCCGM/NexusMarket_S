package application.domain.dto.order;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parametros opcionales de CreateOrder.
 *
 * El contenido del pedido NO se toma de esta peticion: se construye siempre
 * desde el carrito vigente del comprador y el precio se resuelve en el
 * servidor. La direccion de envio se toma del perfil del comprador.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @Size(max = 1000)
    private String notes;

    /** Codigo ISO de tres letras; el servicio lo normaliza a mayusculas. */
    @Pattern(regexp = "(?i)^[a-z]{3}$", message = "La moneda debe ser un codigo ISO de tres letras")
    private String currency = "USD";
}
