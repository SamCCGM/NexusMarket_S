package application.persistence.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDocument {

    private String productId;
    private String variantId;
    private String productName;
    private String variantName;
    private String productImageUrl;
    private BigDecimal price;
    private Integer quantity;
    private String sellerId;
    private String sellerName;
}