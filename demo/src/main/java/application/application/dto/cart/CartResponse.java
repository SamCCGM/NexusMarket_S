package application.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private String id;
    private Long buyerId;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private int totalItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CartResponse empty() {
        return CartResponse.builder()
                .items(List.of())
                .subtotal(BigDecimal.ZERO)
                .totalItems(0)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private String productId;
        private String variantId;
        private String productName;
        private String variantName;
        private String productImageUrl;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal totalPrice;
        private String sellerId;
        private String sellerName;
    }
}