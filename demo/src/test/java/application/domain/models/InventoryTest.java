package application.domain.models;

import application.domain.enums.InventoryStatus;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de los invariantes de Inventory: nunca negativo, reserva solo de
 * disponible, DAMAGED no reservable.
 */
class InventoryTest {

    private Inventory inventory(int qty, InventoryStatus status) {
        Inventory inv = new Inventory();
        inv.setQuantity(qty);
        inv.setStatus(status);
        return inv;
    }

    @Test
    @DisplayName("availableQuantity = quantity - reserved - damaged")
    void availableQuantity() {
        Inventory inv = inventory(50, InventoryStatus.AVAILABLE);
        inv.reserve(10);
        inv.markDamaged(5);
        assertEquals(35, inv.getAvailableQuantity());
        assertEquals(50, inv.getQuantity());
    }

    @Test
    @DisplayName("no se permite reservar mas de lo disponible")
    void reserveMoreThanAvailableThrows() {
        Inventory inv = inventory(5, InventoryStatus.AVAILABLE);
        assertThrows(BusinessRuleException.class, () -> inv.reserve(6));
    }

    @Test
    @DisplayName("cantidades menores o iguales a cero nunca son aceptadas")
    void nonPositiveAmountThrows() {
        Inventory inv = inventory(5, InventoryStatus.AVAILABLE);
        assertThrows(BusinessRuleException.class, () -> inv.reserve(0));
        assertThrows(BusinessRuleException.class, () -> inv.reserve(-1));
        assertThrows(BusinessRuleException.class, () -> inv.registerIncome(0));
        assertThrows(BusinessRuleException.class, () -> inv.registerSaleExit(-3));
    }

    @Test
    @DisplayName("inventario DAMAGED no se reserva (regla del SDD)")
    void damagedCannotBeReserved() {
        Inventory inv = inventory(10, InventoryStatus.DAMAGED);
        assertThrows(BusinessRuleException.class, () -> inv.reserve(1));
    }

    @Test
    @DisplayName("SALE_EXIT descuenta y libera la reserva correspondiente")
    void saleExit() {
        Inventory inv = inventory(10, InventoryStatus.AVAILABLE);
        inv.reserve(4);
        inv.registerSaleExit(4);
        assertEquals(6, inv.getQuantity());
        assertEquals(0, inv.getReservedQuantity());
        assertEquals(6, inv.getAvailableQuantity());
    }

    @Test
    @DisplayName("no puede retirarse mas de lo registrado")
    void saleExitMoreThanTotalThrows() {
        Inventory inv = inventory(3, InventoryStatus.AVAILABLE);
        assertThrows(BusinessRuleException.class, () -> inv.registerSaleExit(4));
    }

    @Test
    @DisplayName("releaseReservation nunca deja valores negativos")
    void releaseReservationFloor() {
        Inventory inv = inventory(10, InventoryStatus.AVAILABLE);
        inv.reserve(2);
        inv.releaseReservation(5);
        assertEquals(0, inv.getReservedQuantity());
    }

    @Test
    @DisplayName("adjustTo no admite existencias negativas y ajusta la reserva")
    void adjust() {
        Inventory inv = inventory(10, InventoryStatus.AVAILABLE);
        inv.reserve(8);
        inv.adjustTo(5);
        assertEquals(5, inv.getQuantity());
        assertEquals(5, inv.getReservedQuantity());
        assertThrows(BusinessRuleException.class, () -> inv.adjustTo(-1));
    }

    @Test
    @DisplayName("RETURN reincorpora unidades (aprobacion no toca inventario, el return fisico si)")
    void registerReturn() {
        Inventory inv = inventory(5, InventoryStatus.AVAILABLE);
        inv.registerReturn(2);
        assertEquals(7, inv.getQuantity());
    }

    @Test
    @DisplayName("lowStock con umbral")
    void lowStock() {
        Inventory inv = inventory(3, InventoryStatus.AVAILABLE);
        assertTrue(inv.isLowStock(3));
        assertFalse(inv.isLowStock(2));
    }

    @Test
    @DisplayName("MarkDamaged retira disponibilidad sin alterar el total fisico")
    void markDamaged() {
        Inventory inv = inventory(10, InventoryStatus.AVAILABLE);
        inv.markDamaged(3);
        assertEquals(3, inv.getDamagedQuantity());
        assertEquals(7, inv.getAvailableQuantity());
        assertEquals(10, inv.getQuantity());
    }

    @Test
    @DisplayName("no se pueden marcar como danadas mas unidades que las disponibles")
    void markDamagedMoreThanAvailableThrows() {
        Inventory inv = inventory(4, InventoryStatus.AVAILABLE);
        inv.reserve(3);
        assertThrows(BusinessRuleException.class, () -> inv.markDamaged(2));
        assertThrows(BusinessRuleException.class, () -> inv.markDamaged(0));
    }

    @Test
    @DisplayName("adjustTo mantiene coherente reserved + damaged")
    void adjustKeepsDamagedAndReservedCoherent() {
        Inventory inv = inventory(10, InventoryStatus.AVAILABLE);
        inv.reserve(4);
        inv.markDamaged(3);
        inv.adjustTo(5);
        assertEquals(5, inv.getQuantity());
        assertEquals(4, inv.getReservedQuantity());
        assertEquals(1, inv.getDamagedQuantity());
        assertTrue(inv.getAvailableQuantity() >= 0);
    }
}
