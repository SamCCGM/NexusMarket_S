package application.dto.product;

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
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String sku;
    private String status;
    private String category;
    private String brand;
    private String images;
    private Double weight;
    private String dimensions;
    private Long sellerId;
    private String sellerName;
    private List<ProductVariantResponse> variants;
    private String productType;
}