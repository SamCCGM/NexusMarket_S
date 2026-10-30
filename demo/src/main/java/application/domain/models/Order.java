package application.domain.models;

import application.domain.enums.OrderStatus;
import application.domain.valueObjects.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Order formaliza la compra de un comprador sobre los productos de su carrito.
 *
 * Estados: PENDING_PAYMENT -> PAID -> DISPATCHED -> DELIVERED -> FINALIZED.
 * Las transiciones no admiten saltos arbitrarios: DISPATCHED solo corresponde
 * a pedidos con despacho fisico; los productos virtuales se entregan
 * digitalmente tras confirmar el pago (PAID -> DELIVERED). Un pedido
 * FINALIZED no puede modificarse.
 */
@Getter
@Setter
@NoArgsConstructor
public class Order {

    private int orderId;
    private Buyer buyer;
    private List<OrderItem> items = new ArrayList<>();
    private OrderStatus status;
    private boolean paid;

    /**
     * Transiciones validas segun la especificacion. Un pedido 100% virtual
     * no despacha fisicamente, pero si entrega digitalmente.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null || status == null) {
            return false;
        }
        return switch (status) {
            case PENDING_PAYMENT -> target == OrderStatus.PAID;
            case PAID -> requiresPhysicalDispatch()
                    ? target == OrderStatus.DISPATCHED
                    : target == OrderStatus.DELIVERED;
            case DISPATCHED -> target == OrderStatus.DELIVERED;
            case DELIVERED -> target == OrderStatus.FINALIZED;
            case FINALIZED -> false;
        };
    }

    /** Cambia el estado respetando la maquina definida; no permite saltos. */
    public void transitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new application.exception.BusinessRuleException(
                    "Transicion de estado invalida: " + status + " -> " + target);
        }
        status = target;
    }

    /** Procesar el pago solo marca el pedido pagado si el pago se confirmo. */
    public void registerSuccessfulPayment() {
        if (!isPaymentConfirmed()) {
            throw new application.exception.BusinessRuleException(
                    "El pedido solo pasa a PAID si el pago se confirma; un intento fallido no se marca como pagado");
        }
        if (status == OrderStatus.PENDING_PAYMENT) {
            transitionTo(OrderStatus.PAID);
        }
        paid = true;
    }

    public boolean isPaymentConfirmed() {
        return paid;
    }

    /** Un pedido finalizado no puede modificarse. */
    public void assertModifiable() {
        if (status == OrderStatus.FINALIZED) {
            throw new application.exception.BusinessRuleException(
                    "Un pedido finalizado no puede modificarse");
        }
    }

    /** DISPATCHED solo aplica a pedidos que efectivamente requieren despacho fisico. */
    public boolean requiresPhysicalDispatch() {
        return items.stream().anyMatch(i -> i.isPhysicalPurchase());
    }

    /** El subtotal corresponde a los importes registrados en sus lineas. */
    public Money subtotal() {
        Money total = null;
        for (OrderItem item : items) {
            Money line = item.lineTotal();
            total = total == null ? line : total.add(line);
        }
        return total;
    }
}
