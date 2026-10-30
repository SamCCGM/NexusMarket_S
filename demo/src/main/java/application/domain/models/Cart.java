package application.domain.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Cart agrupa las compras en preparacion de un comprador.
 * Cada carrito pertenece a un unico comprador y solo el propietario puede
 * modificarlo. Agregar productos no reserva inventario por si mismo.
 */
@Getter
@Setter
@NoArgsConstructor
public class Cart {

    private int cartId;
    private Buyer buyer;
    private List<CartItem> items = new java.util.ArrayList<>();

    /**
     * AddItemToCart: agrega un producto o suma la cantidad de la linea
     * existente del mismo producto.
     */
    public void addItem(CartItem item) {
        if (buyer == null) {
            throw new application.exception.BusinessRuleException(
                    "Cada carrito debe pertenecer a un comprador");
        }
        if (!buyer.canOperate()) {
            throw new application.exception.BusinessRuleException(
                    "Un comprador bloqueado no puede utilizar su carrito");
        }
        item.assertValidItem();

        CartItem existing = items.stream()
                .filter(i -> i.getProduct() != null
                        && i.getProduct().getProductId() == item.getProduct().getProductId())
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
        } else {
            items.add(item);
        }
    }

    /**
     * UpdateCartItem: la nueva cantidad debe ser mayor que cero para
     * conservar la linea; cantidades cero o negativas no son validas.
     */
    public void updateItemQuantity(int productId, int newQuantity) {
        if (newQuantity <= 0) {
            throw new application.exception.BusinessRuleException(
                    "La cantidad debe ser mayor que cero; para retirar la linea use RemoveItemFromCart");
        }
        CartItem item = findLine(productId);
        item.setQuantity(newQuantity);
    }

    /**
     * RemoveItemFromCart: los productos eliminados no forman parte del
     * pedido que se cree posteriormente.
     */
    public void removeItem(int productId) {
        CartItem item = findLine(productId);
        items.remove(item);
    }

    public boolean canBeConvertedToOrder() {
        return !items.isEmpty();
    }

    private CartItem findLine(int productId) {
        return items.stream()
                .filter(i -> i.getProduct() != null
                        && i.getProduct().getProductId() == productId)
                .findFirst()
                .orElseThrow(() -> new application.exception.BusinessRuleException(
                        "La linea indicada no existe en el carrito"));
    }
}
