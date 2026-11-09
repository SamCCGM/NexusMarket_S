package application.domain.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseRequest {
    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Size(min = 5, max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 20)
    private String postalCode;

    @NotNull
    @Pattern(regexp = "(?i)^(MARKETPLACE|SELLER)$",
            message = "El tipo de bodega debe ser MARKETPLACE o SELLER")
    private String type; // MARKETPLACE o SELLER

    @Positive
    private Integer capacity;

    // Null en una actualizacion significa "conservar el estado actual".
    private Boolean active;
}