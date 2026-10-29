package application.domain.ports;

import application.domain.valueobjects.Money;

/**
 * Output Port para el procesamiento de pagos.
 * El procesamiento real pertenece a un componente externo; el dominio
 * no debe depender de una plataforma o proveedor específico de pagos.
 */
public interface PaymentPort {

    /**
     * Solicita el procesamiento de un pago asociado a un pedido.
     *
     * @return {@code true} si el pago se confirmó correctamente.
     */
    boolean processPayment(Long orderId, Money amount);

    /**
     * Verifica el resultado de una operación de pago previa.
     */
    boolean isPaymentConfirmed(Long orderId);

}
