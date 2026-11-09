package application.domain.dto.cart;

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
public class CartItemRequest {
    @NotNull
    @Positive
    private Long productId;

    @Positive
    private Long variantId;

    @NotBlank
    @Size(max = 200)
    private String productName;

    @Size(max = 100)
    private String variantName;

    @Size(max = 1000)
    private String productImageUrl;

    @NotNull
    @Positive
    private BigDecimal price;

    @NotNull
    @Positive
    private Integer quantity;

    @Positive
    private Long sellerId;

    @Size(max = 150)
    private String sellerName;
}
