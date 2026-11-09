package application.domain.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Size(max = 5000)
    private String images; // JSON array

    @Positive
    private Double weight;

    @Size(max = 100)
    private String dimensions; // JSON

    /** PHYSICAL (por defecto) o DIGITAL. */
    @Pattern(regexp = "(?i)^(PHYSICAL|DIGITAL)$",
            message = "El tipo de producto debe ser PHYSICAL o DIGITAL")
    private String productType;

    // Datos propios de los productos digitales
    @Size(max = 1000)
    private String downloadUrl;

    @Positive
    private Long fileSizeMb;

    @Size(max = 50)
    private String fileFormat;

    private Boolean licenseKeyRequired;

    @Positive
    private Integer maxDownloads;

    @Valid
    @Size(max = 50)
    private List<ProductVariantRequest> variants;
}