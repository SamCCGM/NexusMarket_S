package application.domain.ports;

/**
 * Output Port para el envío de comunicaciones relacionadas con las
 * operaciones del Marketplace.
 *
 * La implementación concreta puede utilizar diferentes mecanismos de
 * comunicación; el dominio no depende de un proveedor específico.
 */
public interface NotificationPort {

    /**
     * Envía una notificación al usuario indicado.
     */
    void sendNotification(String recipientEmail, String subject, String message);

    /**
     * Comunica un evento relevante del proceso de compra.
     */
    void notifyOrderEvent(Long orderId, String event);

}
