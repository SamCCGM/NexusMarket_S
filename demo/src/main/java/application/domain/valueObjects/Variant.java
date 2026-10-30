package application.domain.valueObjects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Variant (Value Object).
 * Representa una característica diferenciadora de un producto
 * (color, talla, modelo, etc.) mediante un par nombre/valor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Variant {

    private String name;
    private String value;

    /** Una variante debe definir el atributo que la diferencia. */
    public void assertValid() {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            throw new application.exception.BusinessRuleException(
                    "La variante debe definir nombre y valor del atributo");
        }
    }
}
