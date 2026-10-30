package application.domain.models;

import application.domain.enums.OrderStatus;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de devoluciones y reembolsos: solo un pedido con pago confirmado,
 * motivo y cantidad descrita, REQUESTED -> APPROVED y solo APPROVED pasa a
 * reembolso.
 */
class ReturnAndRefundTest {

    /** Construye el retorno pedido/linea y marca el pedido como entregado y pagado. */
    private Return atDeliveredOrder() throws ReflectiveOperationException {
        OrderItem item = new OrderItem();
        item.setProduct(new PhysicalProduct());
        item.setQuantity(2);

        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        Field paid = Order.class.getDeclaredField("paid");
        paid.setAccessible(true);
        paid.set(order, true);

        Return r = new Return();
        r.setOrder(order);
        r.setOrderItem(item);
        r.setReason("Producto inservible");
        r.setQuantity(1);
        return r;
    }

    @Test
    @DisplayName("solicitud valida queda en REQUESTED")
    void validRequest() throws ReflectiveOperationException {
        Return r = atDeliveredOrder();
        r.assertValidRequest();
        assertEquals("REQUESTED", r.getStatus());
    }

    @Test
    @DisplayName("sin motivo -> rechazada")
    void missingReasonThrows() throws ReflectiveOperationException {
        Return r = atDeliveredOrder();
        r.setReason("  ");
        assertThrows(BusinessRuleException.class, r::assertValidRequest);
    }

    @Test
    @DisplayName("la cantidad devuelta no puede superar la cantidad comprada")
    void quantityBiggerThanPurchasedThrows() throws ReflectiveOperationException {
        Return r = atDeliveredOrder();
        r.setQuantity(3);
        assertThrows(BusinessRuleException.class, r::assertValidRequest);
    }

    @Test
    @DisplayName("solo se aprueba una solicitud REQUESTED")
    void approveOnlyOnce() throws ReflectiveOperationException {
        Return r = atDeliveredOrder();
        r.assertValidRequest();
        r.approve();
        assertEquals("APPROVED", r.getStatus());
        assertThrows(BusinessRuleException.class, r::approve);
    }

    @Test
    @DisplayName("solo una devolucion aprobada puede pasar a reembolso")
    void refundRequiresApprovedReturn() throws ReflectiveOperationException {
        Return r = atDeliveredOrder();
        r.assertValidRequest();

        Refund refund = new Refund();
        refund.setReturnRequest(r);
        assertThrows(BusinessRuleException.class, refund::assertCanBeProcessed);

        r.approve();
        // sin monto positivo no hay reembolso
        assertThrows(BusinessRuleException.class, refund::assertCanBeProcessed);

        // monto que corresponde al precio conservado de la linea x cantidad devuelta
        OrderItem item = r.getOrderItem();
        item.setUnitPrice(new application.domain.valueObjects.Money(new java.math.BigDecimal("5.00"), "USD"));
        refund.setAmount(item.getUnitPrice().multiply(r.getQuantity()));
        assertDoesNotThrow(refund::assertCanBeProcessed);
        assertEquals("COMPLETED", refund.getStatus());
    }
}
