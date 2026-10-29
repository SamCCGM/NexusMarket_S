package application.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantRequest {
    @NotBlank
    @Size(min = 1, max = 100)
    private String variantName;

    @Size(max = 100)
    private String sku;

    @NotNull
    @Positive
    private BigDecimal price;

    private Integer stock = 0;

    private String attributes; // JSON

    private boolean active = true;
}