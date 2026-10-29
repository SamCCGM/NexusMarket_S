package application.infrastructure.notification;

import application.domain.ports.NotificationPort;
import application.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación concreta (adaptador) del Output Port {@link NotificationPort}.
 *
 * Pertenece a la capa externa: el dominio solo conoce la interfaz. En una
 * integración real, aquí se enviarían correos/SMS mediante un proveedor.
 *
 * En esta primera entrega el adaptador registra la notificación (log) para
 * dejar trazabilidad sin depender de un proveedor externo.
 */
@Slf4j
@Component
public class NotificationAdapter implements NotificationPort {

    @Override
    public void sendNotification(String recipientEmail, String subject, String message) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new BusinessRuleException("Recipient email is required to send a notification");
        }

        log.info("[NOTIFICATION] to={} subject={} message={}", recipientEmail, subject, message);
    }

    @Override
    public void notifyOrderEvent(Long orderId, String event) {
        log.info("[ORDER EVENT] orderId={} event={}", orderId, event);
    }
}
