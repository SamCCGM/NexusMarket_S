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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    @NotBlank
    @Size(min = 2, max = 200)
    private String name;

    @Size(max = 5000)
    private String description;

    @NotNull
    @Positive
    private BigDecimal basePrice;

    @Size(max = 100)
    private String sku;

    @Size(max = 100)
    private String category;

    @Size(max = 100)
    private String brand;

    private String images; // JSON array

    private Double weight;

    private String dimensions; // JSON

    private List<ProductVariantRequest> variants;
}