package application.domain.valueObjects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Address (Value Object).
 * Representa una ubicación utilizada dentro del Marketplace para
 * identificar un lugar de entrega.
 *
 * La especificación no define los componentes internos de una dirección,
 * por lo que se conserva únicamente el valor de la ubicación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    private String value;

    /** Una direccion necesita un valor identificador para ser utilizada. */
    public boolean hasText() {
        return value != null && !value.isBlank();
    }

    /** Valida que la direccion sirva como destino (regla de envios). */
    public void assertValidDestination() {
        if (!hasText()) {
            throw new application.exception.BusinessRuleException(
                    "El envio debe tener un destino valido");
        }
    }
}
