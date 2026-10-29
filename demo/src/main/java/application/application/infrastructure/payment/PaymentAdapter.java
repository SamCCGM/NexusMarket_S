package application.application.infrastructure.payment;

import application.application.domain.ports.PaymentPort;
import application.application.domain.valueobjects.Money;
import application.application.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación concreta (adaptador) del Output Port {@link PaymentPort}.
 *
 * Pertenece a la capa externa de la arquitectura: el dominio solo conoce la
 * interfaz, no este adaptador. En una integración real, aquí se delegaría la
 * operación al proveedor de pagos correspondiente.
 *
 * En esta primera entrega el adaptador simula la aprobación del pago y
 * conserva el resultado en memoria para poder consultarlo después.
 */
@Slf4j
@Component
public class PaymentAdapter implements PaymentPort {

    private final Map<Long, Boolean> paymentResults = new ConcurrentHashMap<>();

    @Override
    public boolean processPayment(Long orderId, Money amount) {
        if (orderId == null) {
            throw new BusinessRuleException("Order id is required to process a payment");
        }
        if (amount == null || amount.getAmount() == null || amount.getAmount().signum() <= 0) {
            throw new BusinessRuleException("A positive payment amount is required");
        }

        // Simulación: se aprueba el pago para permitir avanzar el flujo de compra.
        // En producción, esta llamada se delegaría al proveedor externo de pagos.
        boolean confirmed = true;
        paymentResults.put(orderId, confirmed);

        log.info("[PAYMENT] orderId={} amount={} {} -> confirmed={}",
                orderId, amount.getAmount(), amount.getCurrency(), confirmed);
        return confirmed;
    }

    @Override
    public boolean isPaymentConfirmed(Long orderId) {
        return orderId != null && paymentResults.getOrDefault(orderId, false);
    }
}
