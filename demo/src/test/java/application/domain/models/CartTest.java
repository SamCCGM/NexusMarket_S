package application.domain.models;

import application.domain.enums.ProductStatus;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del carrito: lineas validas, fusion de productos repetidos,
 * propietario activo y conversion a pedido.
 */
class CartTest {

    private Buyer activeBuyer() {
        Buyer buyer = new Buyer();
        buyer.setName("Ana");
        buyer.setEmail("ana@example.com");
        buyer.setStatus(UserStatus.ACTIVE);
        return buyer;
    }

    private CartItem line(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    @DisplayName("addItem agrega una linea nueva")
    void addItemNewLine() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        cart.addItem(line(new PhysicalProduct(), 2));
        assertEquals(1, cart.getItems().size());
    }

    @Test
    @DisplayName("addItem fusiona la cantidad del mismo producto")
    void addItemMergesSameProduct() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());

        Product product = new PhysicalProduct();
        cart.addItem(line(product, 2));
        cart.addItem(line(product, 3));

        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("no se permiten lineas sin producto o con cantidad invalida")
    void invalidLineThrows() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        assertThrows(BusinessRuleException.class, () -> cart.addItem(line(null, 1)));
        assertThrows(BusinessRuleException.class, () -> cart.addItem(line(new PhysicalProduct(), 0)));
    }

    @Test
    @DisplayName("productos suspendidos o descontinuados no entran a nuevas compras")
    void nonPurchasableProductThrows() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        Product suspended = new PhysicalProduct();
        suspended.setStatus(ProductStatus.SUSPENDED);
        assertThrows(BusinessRuleException.class, () -> cart.addItem(line(suspended, 1)));
    }

    @Test
    @DisplayName("un comprador bloqueado no puede utilizar su carrito")
    void blockedBuyerCannotUseCart() {
        Cart cart = new Cart();
        Buyer buyer = activeBuyer();
        buyer.setStatus(UserStatus.BLOCKED);
        cart.setBuyer(buyer);
        assertThrows(BusinessRuleException.class, () -> cart.addItem(line(new PhysicalProduct(), 1)));
    }

    @Test
    @DisplayName("cada carrito debe pertenecer a un comprador")
    void cartRequiresBuyer() {
        Cart cart = new Cart();
        assertThrows(BusinessRuleException.class, () -> cart.addItem(line(new PhysicalProduct(), 1)));
    }

    @Test
    @DisplayName("updateItemQuantity solo admite cantidades positivas")
    void updateQuantity() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        Product product = new PhysicalProduct();
        cart.addItem(line(product, 2));

        cart.updateItemQuantity(product.getProductId(), 7);
        assertEquals(7, cart.getItems().get(0).getQuantity());
        assertThrows(BusinessRuleException.class, () -> cart.updateItemQuantity(product.getProductId(), 0));
    }

    @Test
    @DisplayName("removeItem retira la linea y el pedido posterior no la incluye")
    void removeItem() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        Product product = new PhysicalProduct();
        cart.addItem(line(product, 2));

        cart.removeItem(product.getProductId());
        assertTrue(cart.getItems().isEmpty());
        assertThrows(BusinessRuleException.class, () -> cart.removeItem(product.getProductId()));
    }

    @Test
    @DisplayName("carrito vacio no convierte a pedido")
    void emptyCartCannotConvert() {
        Cart cart = new Cart();
        cart.setBuyer(activeBuyer());
        assertFalse(cart.canBeConvertedToOrder());
        cart.addItem(line(new PhysicalProduct(), 1));
        assertTrue(cart.canBeConvertedToOrder());
    }

    @Test
    @DisplayName("CartItem valida producto y cantidad")
    void cartItemValidation() {
        CartItem item = new CartItem();
        assertThrows(BusinessRuleException.class, item::assertValidItem);
        item.setProduct(new DigitalProduct());
        item.setQuantity(-1);
        assertThrows(BusinessRuleException.class, item::assertValidItem);
        item.setQuantity(1);
        assertDoesNotThrow(item::assertValidItem);
    }
}
