package application.domain.models;

import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.domain.valueObjects.Address;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de identidad y estado de los operadores del sistema: cuenta activa
 * para operar, bloqueo sin borrar historial y reactivacion.
 */
class UserTest {

    private Buyer defaultBuyer() {
        Buyer buyer = new Buyer();
        buyer.setName("Ana");
        buyer.setEmail("ana@example.com");
        buyer.setRole(UserRole.BUYER);
        buyer.setStatus(UserStatus.ACTIVE);
        return buyer;
    }

    @Test
    @DisplayName("solo un usuario activo puede operar")
    void canOperate() {
        assertTrue(defaultBuyer().canOperate());
        Buyer blocked = defaultBuyer();
        blocked.setStatus(UserStatus.BLOCKED);
        assertFalse(blocked.canOperate());
    }

    @Test
    @DisplayName("identidad valida: nombre, correo y rol unico obligatorios")
    void identityValidation() {
        Buyer buyer = defaultBuyer();
        assertDoesNotThrow(buyer::assertIdentity);

        buyer.setName(null);
        assertThrows(BusinessRuleException.class, buyer::assertIdentity);

        buyer.setName("Ana");
        buyer.setEmail("");
        assertThrows(BusinessRuleException.class, buyer::assertIdentity);
    }

    @Test
    @DisplayName("bloquear o activar una cuenta ya finalizada no duplica el estado")
    void blockAndActivateGuards() {
        Buyer buyer = defaultBuyer();
        buyer.block();
        assertEquals(UserStatus.BLOCKED, buyer.getStatus());
        assertThrows(BusinessRuleException.class, buyer::block);

        buyer.activate();
        assertEquals(UserStatus.ACTIVE, buyer.getStatus());
        assertThrows(BusinessRuleException.class, buyer::activate);
    }

    @Test
    @DisplayName("comprador con direccion principal valida para entregas fisicas")
    void deliveryAddress() {
        Buyer buyer = defaultBuyer();
        assertFalse(buyer.hasValidDeliveryAddress());

        buyer.setPrimaryAddress(new Address("Cra 7 # 40-12"));
        assertTrue(buyer.hasValidDeliveryAddress());
    }

    private Seller sellerGuardsHelper() {
        Seller seller = new Seller();
        seller.setName("Tienda");
        seller.setEmail("tienda@example.com");
        seller.setRole(UserRole.SELLER);
        seller.setStatus(UserStatus.ACTIVE);
        return seller;
    }

    @Test
    @DisplayName("vendedor bloqueado no puede comerciar ni actualizar sus datos")
    void sellerGuards() {
        Seller seller = sellerGuardsHelper();
        assertTrue(seller.canOperateInMarketplace());
        assertDoesNotThrow(seller::assertCanUpdateSellerProfile);

        seller.setStatus(UserStatus.BLOCKED);
        assertFalse(seller.canOperateInMarketplace());
        assertThrows(BusinessRuleException.class, seller::assertCanUpdateSellerProfile);
    }
}
