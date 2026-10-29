package application.domain.valueobjects;

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

}
