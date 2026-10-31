package application.domain.dto.product;

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

    /** PHYSICAL (por defecto) o DIGITAL. */
    private String productType;

    // Datos propios de los productos digitales
    private String downloadUrl;
    private Long fileSizeMb;
    private String fileFormat;
    private Boolean licenseKeyRequired;
    private Integer maxDownloads;

    private List<ProductVariantRequest> variants;
}