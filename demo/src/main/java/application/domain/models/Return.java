package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Return registra la solicitud de devolucion de una compra.
 * Solo una devolucion aprobada puede pasar al proceso de reembolso, y la
 * aprobacion no devuelve unidades al inventario automaticamente.
 */
@Getter
@Setter
@NoArgsConstructor
public class Return {

    private int returnId;
    private Order order;
    private OrderItem orderItem;
    private String reason;
    private Integer quantity;
    private String status;  // REQUESTED -> APPROVED -> RECEIVED
    private LocalDateTime processedAt;
    private LocalDateTime receivedAt;

    /** Reglas: pedido asociado del comprador, motivo descrito y cantidad valida. */
    public void assertValidRequest() {
        if (order == null || orderItem == null) {
            throw new application.exception.BusinessRuleException(
                    "La devolucion debe identificar el pedido y la linea del producto");
        }
        if (reason == null || reason.isBlank()) {
            throw new application.exception.BusinessRuleException(
                    "La solicitud debe describir el motivo");
        }
        if (quantity == null || quantity <= 0) {
            throw new application.exception.BusinessRuleException(
                    "La cantidad a devolver debe ser mayor que cero");
        }
        if (orderItem.getQuantity() < quantity) {
            throw new application.exception.BusinessRuleException(
                    "La cantidad devuelta no puede superar la cantidad comprada");
        }
        if (order.getStatus() == null || !order.isPaymentConfirmed()) {
            throw new application.exception.BusinessRuleException(
                    "La devolucion debe corresponder a un pedido valido con pago confirmado");
        }
        status = "REQUESTED";
    }

    /** ApproveReturn: solo solicitudes pendientes se aprueban. */
    public void approve() {
        if (!"REQUESTED".equals(status)) {
            throw new application.exception.BusinessRuleException(
                    "Solo una solicitud REQUESTED puede aprobarse (estado actual: " + status + ")");
        }
        status = "APPROVED";
        processedAt = LocalDateTime.now();
    }

    /** Solo una devolucion aprobada puede pasar al proceso de reembolso. */
    public boolean canProceedToRefund() {
        // La recepcion de unidades es posterior e independiente del reembolso.
        return "APPROVED".equals(status) || "RECEIVED".equals(status);
    }

    /**
     * ReceiveReturn: registra la recepcion y revision de las unidades. Solo
     * aplica a devoluciones aprobadas; el reintegro efectivo al inventario lo
     * realiza ReturnStock con las unidades declaradas aptas.
     */
    public void receive() {
        if (!"APPROVED".equals(status)) {
            throw new application.exception.BusinessRuleException(
                    "Solo una devolucion APPROVED puede recibirse (estado actual: " + status + ")");
        }
        status = "RECEIVED";
        receivedAt = LocalDateTime.now();
    }
}
