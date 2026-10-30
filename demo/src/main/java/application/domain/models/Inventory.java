package application.domain.models;

import application.domain.enums.InventoryStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inventory representa las existencias de un producto fisico en una bodega.
 * Bajo ninguna circunstancia se permiten cantidades negativas y el inventario
 * marcado como DAMAGED no puede reservarse.
 */
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    private int inventoryId;
    private Warehouse warehouse;
    private PhysicalProduct product;
    private int quantity;
    private int reservedQuantity;
    private InventoryStatus status;

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public boolean isLowStock(int threshold) {
        return getAvailableQuantity() <= threshold;
    }

    private void assertPositive(int amount) {
        if (amount <= 0) {
            throw new application.exception.BusinessRuleException(
                    "La cantidad debe ser mayor que cero (nunca negativa)");
        }
    }

    /** AddStock (INCOME): registra el ingreso de unidades a la bodega. */
    public void registerIncome(int amount) {
        assertPositive(amount);
        quantity += amount;
    }

    /** ReserveStock (RESERVATION): reserva solo unidades disponibles. */
    public void reserve(int amount) {
        assertPositive(amount);
        if (status == InventoryStatus.DAMAGED) {
            throw new application.exception.BusinessRuleException(
                    "El inventario damagado no puede reservarse");
        }
        if (getAvailableQuantity() < amount) {
            throw new application.exception.BusinessRuleException(
                    "No puede reservarse una cantidad superior a la disponible");
        }
        reservedQuantity += amount;
    }

    /** Libera una reserva previa (pago fallido, pedido cancelado). */
    public void releaseReservation(int amount) {
        assertPositive(amount);
        reservedQuantity = Math.max(0, reservedQuantity - amount);
    }

    /** RemoveStock (SALE_EXIT): salida de unidades por una venta autorizada. */
    public void registerSaleExit(int amount) {
        assertPositive(amount);
        if (quantity < amount) {
            throw new application.exception.BusinessRuleException(
                    "No puede retirarse mas de la cantidad total registrada");
        }
        quantity -= amount;
        releaseReservation(amount);
    }

    /** ReturnStock (RETURN): reincorpora unidades recibidas y revisadas. */
    public void registerReturn(int amount) {
        assertPositive(amount);
        quantity += amount;
    }

    /** AdjustStock (ADJUSTMENT): ajusta a la cantidad verificada. */
    public void adjustTo(int verifiedQuantity) {
        if (verifiedQuantity < 0) {
            throw new application.exception.BusinessRuleException("No se permiten existencias negativas");
        }
        quantity = verifiedQuantity;
        reservedQuantity = Math.min(reservedQuantity, verifiedQuantity);
    }
}
