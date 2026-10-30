package application.domain.models;

import application.domain.enums.OrderStatus;
import application.domain.enums.ShipmentStatus;
import application.domain.valueObjects.Address;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de Shipment: solo pedidos fisicos pagados con destino valido, sin
 * duplicar estados (CREATED -> DISPATCHED -> IN_TRANSIT -> DELIVERED).
 */
class ShipmentTest {

    /** Marca el pedido como pagado por reflexion (el modelo guarda el flag). */
    private void markPaid(Order order, boolean paid) throws ReflectiveOperationException {
        Field field = Order.class.getDeclaredField("paid");
        field.setAccessible(true);
        field.set(order, paid);
    }

    private Order physicalPaidOrder() throws ReflectiveOperationException {
        OrderItem item = new OrderItem();
        item.setProduct(new PhysicalProduct());

        Order order = new Order();
        order.setStatus(OrderStatus.PAID);
        order.setItems(List.of(item));
        markPaid(order, true);
        return order;
    }

    private Shipment shipmentFor(Order order, String destination) {
        Shipment s = new Shipment();
        s.setOrder(order);
        s.setDestination(new Address(destination));
        return s;
    }

    @Test
    @DisplayName("envio valido para pedido fisico pagado con destino")
    void validCreation() throws ReflectiveOperationException {
        Shipment s = shipmentFor(physicalPaidOrder(), "Calle 1 # 2-3");
        assertDoesNotThrow(s::assertCanBeCreated);
        assertEquals(ShipmentStatus.CREATED, s.getStatus());
    }

    @Test
    @DisplayName("sin destino valido no hay envio")
    void missingDestinationThrows() throws ReflectiveOperationException {
        Shipment s = shipmentFor(physicalPaidOrder(), "   ");
        assertThrows(BusinessRuleException.class, s::assertCanBeCreated);
    }

    @Test
    @DisplayName("pedidos con pago pendiente no generan envio")
    void unpaidOrderThrows() throws ReflectiveOperationException {
        Order order = physicalPaidOrder();
        markPaid(order, false);
        Shipment s = shipmentFor(order, "Calle 1");
        assertThrows(BusinessRuleException.class, s::assertCanBeCreated);
    }

    @Test
    @DisplayName("pedido 100% digital no requiere envio")
    void digitalOrderThrows() throws ReflectiveOperationException {
        OrderItem item = new OrderItem();
        item.setProduct(new DigitalProduct());

        Order order = new Order();
        order.setStatus(OrderStatus.PAID);
        order.setItems(List.of(item));
        markPaid(order, true);

        Shipment s = shipmentFor(order, "Calle 1");
        assertThrows(BusinessRuleException.class, s::assertCanBeCreated);
    }

    @Test
    @DisplayName("ciclo completo CREATED -> DISPATCHED -> IN_TRANSIT -> DELIVERED")
    void fullLifecycle() throws ReflectiveOperationException {
        Shipment s = shipmentFor(physicalPaidOrder(), "Calle 1");
        s.assertCanBeCreated();
        s.dispatch();
        s.markInTransit();
        s.confirmDelivery();

        assertEquals(ShipmentStatus.DELIVERED, s.getStatus());
        assertNotNull(s.getActualDeliveryDate());
    }

    @Test
    @DisplayName("no se despacha dos veces ni se confirma entrega de un CREATED")
    void illegalTransitions() throws ReflectiveOperationException {
        Shipment s = shipmentFor(physicalPaidOrder(), "Calle 1");
        s.assertCanBeCreated();

        assertThrows(BusinessRuleException.class, s::confirmDelivery);
        s.dispatch();
        assertThrows(BusinessRuleException.class, s::dispatch);
        s.markInTransit();
        // tras IN_TRANSIT ya no puedo despachar de nuevo
        assertThrows(BusinessRuleException.class, s::dispatch);
        s.confirmDelivery();
        // entregado no vuelve a traslados
        assertThrows(BusinessRuleException.class, s::markInTransit);
    }
}
