package application.domain.dto.billing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GenerateInvoice (SDD - Billing Services).
 * La factura se genera como RESULTADO asociado a un pedido con pago
 * confirmado; no es una entidad persistida en esta primera entrega.
 */
@Data
@Builder
public class InvoiceResponse {
    private String invoiceNumber;
    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private Long buyerId;
    private String buyerName;
    private String buyerEmail;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currency;
    private List<ItemLine> items;
    private LocalDateTime orderCreatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime issuedAt;

    @Data
    @Builder
    public static class ItemLine {
        private Long productId;
        private String productName;
        private String variantName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
