package application.domain.models;

import application.domain.enums.OrderStatus;
import application.domain.valueObjects.Money;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la maquina de estados y reglas de Order:
 * PENDING_PAYMENT -> PAID -> DISPATCHED/DELIVERED -> DELIVERED -> FINALIZED,
 * sin saltos ni modificacion tras FINALIZED.
 */
class OrderTest {

    private Order orderWith(boolean physical) {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        OrderItem item = new OrderItem();
        item.setProduct(physical ? new PhysicalProduct() : new DigitalProduct());
        item.setQuantity(2);
        item.setUnitPrice(new Money(new BigDecimal("5.00"), "USD"));
        order.setItems(List.of(item));
        return order;
    }

    /** Fuerza el estado indicado en el pedido (casos base de la maquina). */
    private Order atStatus(OrderStatus status, boolean physical) {
        Order order = orderWith(physical);
        try {
            Field statusField = Order.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(order, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return order;
    }

    @Test
    @DisplayName("pedido con productos fisicos: PAID solo despacha, no entrega directo")
    void physicalTransitions() {
        Order order = atStatus(OrderStatus.PAID, true);
        assertTrue(order.canTransitionTo(OrderStatus.DISPATCHED));
        assertFalse(order.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("pedido 100% virtual: DELIVERED inmediato tras el pago, DISPATCHED nunca aplica")
    void virtualTransitions() {
        Order order = atStatus(OrderStatus.PAID, false);
        assertTrue(order.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(order.canTransitionTo(OrderStatus.DISPATCHED));
    }

    @Test
    @DisplayName("no se permiten saltos desde PENDING_PAYMENT (solo pasa a PAID)")
    void noJumpsFromPendingPayment() {
        Order order = atStatus(OrderStatus.PENDING_PAYMENT, true);
        assertTrue(order.canTransitionTo(OrderStatus.PAID));
        assertFalse(order.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(order.canTransitionTo(OrderStatus.FINALIZED));
    }

    @Test
    @DisplayName("transicion completa de un pedido fisico")
    void fullPhysicalLifecycle() {
        Order order = orderWith(true);
        order.transitionTo(OrderStatus.PAID);
        order.transitionTo(OrderStatus.DISPATCHED);
        order.transitionTo(OrderStatus.DELIVERED);
        order.transitionTo(OrderStatus.FINALIZED);
        assertEquals(OrderStatus.FINALIZED, order.getStatus());
    }

    @Test
    @DisplayName("un pedido FINALIZED no puede modificarse")
    void finalizedIsFinal() {
        Order order = atStatus(OrderStatus.FINALIZED, true);
        assertFalse(order.canTransitionTo(OrderStatus.DELIVERED));
        assertThrows(BusinessRuleException.class, () -> order.transitionTo(OrderStatus.PAID));
        assertThrows(BusinessRuleException.class, order::assertModifiable);
    }

    @Test
    @DisplayName("saltos de estado invalidos lanzan excepcion")
    void invalidJumpThrows() {
        Order order = atStatus(OrderStatus.PENDING_PAYMENT, true);
        assertThrows(BusinessRuleException.class, () -> order.transitionTo(OrderStatus.DELIVERED));
        Order physical = atStatus(OrderStatus.PAID, true);
        assertThrows(BusinessRuleException.class, () -> physical.transitionTo(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("subtotal corresponde a los importes de sus lineas")
    void subtotalFromItems() {
        Order order = new Order();

        OrderItem first = new OrderItem();
        first.setProduct(new PhysicalProduct());
        first.setQuantity(2);
        first.setUnitPrice(new Money(new BigDecimal("5.00"), "USD"));

        OrderItem second = new OrderItem();
        second.setProduct(new DigitalProduct());
        second.setQuantity(1);
        second.setUnitPrice(new Money(new BigDecimal("7.50"), "USD"));

        order.setItems(List.of(first, second));

        Money subtotal = order.subtotal();
        assertEquals(0, subtotal.getAmount().compareTo(new BigDecimal("17.50")));
    }

    @Test
    @DisplayName("requiresPhysicalDispatch segun el contenido del pedido")
    void requiresPhysicalDispatch() {
        assertTrue(orderWith(true).requiresPhysicalDispatch());
        assertFalse(orderWith(false).requiresPhysicalDispatch());
    }

    @Test
    @DisplayName("un pedido PENDING_PAYMENT puede cancelarse (expira sin pago)")
    void cancelFromPendingPayment() {
        Order order = atStatus(OrderStatus.PENDING_PAYMENT, true);
        assertTrue(order.canTransitionTo(OrderStatus.CANCELLED));
        order.transitionTo(OrderStatus.CANCELLED);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("un pedido pagado no se cancela (requiere devolucion/reembolso) y CANCELLED es terminal")
    void cancelledIsTerminal() {
        assertFalse(atStatus(OrderStatus.PAID, true).canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(atStatus(OrderStatus.CANCELLED, true).canTransitionTo(OrderStatus.PAID));
        assertFalse(atStatus(OrderStatus.CANCELLED, true).canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("lineas de pedido validan producto, cantidad y precio")
    void orderItemValidation() {
        OrderItem item = new OrderItem();
        item.setProduct(new PhysicalProduct());
        item.setQuantity(0);
        item.setUnitPrice(new Money(new BigDecimal("5.00"), "USD"));
        assertThrows(BusinessRuleException.class, item::assertValidLine);

        item.setQuantity(1);
        item.setUnitPrice(new Money(new BigDecimal("0"), "USD"));
        assertThrows(BusinessRuleException.class, item::assertValidLine);

        item.setUnitPrice(new Money(new BigDecimal("5.00"), "USD"));
        assertDoesNotThrow(item::assertValidLine);
    }
}
