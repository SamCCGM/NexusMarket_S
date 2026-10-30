package application.domain.models;

import application.domain.enums.BuyerStatus;
import application.domain.valueObjects.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Buyer representa al usuario que adquiere productos publicados dentro
 * del Marketplace.
 *
 * Es una especialización de User con la información específica necesaria
 * para participar en los procesos comerciales de compra.
 */
@Getter
@Setter
@NoArgsConstructor
public class Buyer extends User {

    private Address primaryAddress;
    private List<Address> additionalAddresses;
    private BuyerStatus buyerStatus;

    /**
     * UpdateBuyer: actualiza los datos del comprador.
     * El comprador puede mantener su informacion solo estando activo.
     */
    public void assertCanUpdateProfile() {
        assertIdentity();
        if (!canOperate()) {
            throw new application.exception.BusinessRuleException(
                    "Un comprador bloqueado no puede actualizar su informacion");
        }
    }

    /** El comprador necesita una direccion principal para compras fisicas. */
    public boolean hasValidDeliveryAddress() {
        return primaryAddress != null && primaryAddress.hasText();
    }
}
