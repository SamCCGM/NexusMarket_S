package application.domain.models;

import application.domain.valueObjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Refund registra el reembolso correspondiente a una devolucion aprobada.
 * El reembolso y la reincorporacion de unidades son operaciones distintas: el
 * inventario solo se actualiza cuando las unidades fueron recibidas y aptas.
 */
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    private int refundId;
    private Return returnRequest;
    private Money amount;
    private String status;
    private LocalDateTime processedAt;

    /** Reglas: devolucion aprobada de un pedido con pago confirmado. */
    public void assertCanBeProcessed() {
        if (returnRequest == null) {
            throw new application.exception.BusinessRuleException(
                    "El reembolso debe estar asociado a una devolucion existente");
        }
        if (!returnRequest.canProceedToRefund()) {
            throw new application.exception.BusinessRuleException(
                    "Solo puede reembolsarse una devolucion aprobada");
        }
        if (returnRequest.getOrder() != null && !returnRequest.getOrder().isPaymentConfirmed()) {
            throw new application.exception.BusinessRuleException(
                    "El reembolso corresponde a un pedido con pago confirmado");
        }
        if (amount == null || !amount.isPositive()) {
            throw new application.exception.BusinessRuleException(
                    "El monto del reembolso debe ser mayor que cero");
        }
        status = "COMPLETED";
        processedAt = LocalDateTime.now();
    }
}
