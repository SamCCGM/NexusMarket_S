package application.dto.order;

import jakarta.validation.constraints.NotNull;
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
public class OrderRequest {
    @NotNull
    private Long buyerId;

    @NotNull
    private List<OrderItemRequest> items;

    private String shippingAddress; // JSON
    private String billingAddress; // JSON
    private String notes;
    private String currency = "USD";
}