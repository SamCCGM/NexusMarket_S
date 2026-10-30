package application.domain.dto.returns;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReturnResponse {
    private Long id;
    private String returnNumber;
    private Long orderId;
    private String orderNumber;
    private Long orderItemId;
    private String productName;
    private Long buyerId;
    private String reason;
    private Integer quantity;
    private BigDecimal refundAmount;
    private String status;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
