package application.service;

import application.domain.enums.ProductStatus;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.CartDocument;
import application.persistence.mongodb.document.CartItemDocument;
import application.persistence.mongodb.repository.CartDocumentRepository;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicios de dominio del Carrito (SDD - Cart Services).
 *
 * Cada carrito pertenece a un único comprador y solo el comprador propietario
 * puede modificarlo. La disponibilidad se puede consultar aquí, pero su
 * validación definitiva se repite durante la creación del pedido.
 */
@Service
public class CartMongoService {

    @Autowired
    private CartDocumentRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    // ==================== Domain Services (SDD - Cart Services) ====================

    /**
     * AddItemToCart (Actor: Buyer).
     * Agrega un producto al carrito del comprador o actualiza la línea existente
     * si el producto ya estaba (se suman las cantidades).
     *
     * Reglas de negocio:
     * - Cada carrito pertenece a un comprador (acotado por buyerId).
     * - Cada CartItem debe referenciar un producto existente.
     * - La cantidad solicitada debe ser mayor que cero.
     * - Solo se agregan productos aptos para nuevas compras (PUBLISHED) y de
     *   vendedores que puedan participar en el Marketplace (ACTIVO).
     * - Agregar al carrito no reserva unidades por sí solo.
     */
    public CartDocument addItem(Long buyerId, CartItemDocument item) {
        validateItemForCart(item);

        CartDocument cart = getOrCreateCart(buyerId);

        Optional<CartItemDocument> existingItem = cart.getItems().stream()
                .filter(i -> Objects.equals(i.getProductId(), item.getProductId())
                        && Objects.equals(i.getVariantId(), item.getVariantId()))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + item.getQuantity());
        } else {
            cart.getItems().add(item);
        }

        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    /**
     * UpdateCartItem (Actor: Buyer).
     * Actualiza la cantidad de una línea del carrito propio.
     *
     * Reglas de negocio:
     * - El comprador solo modifica su propio carrito.
     * - La línea debe existir.
     * - La cantidad debe ser mayor que cero (para eliminar la línea se usa
     *   RemoveItemFromCart).
     * - Se conservan los demás datos de la línea (precio, nombre, vendedor).
     */
    public CartDocument updateItemQuantity(Long buyerId, String productId, String variantId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero");
        }

        CartDocument cart = cartRepository.findByBuyerId(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for buyer: " + buyerId));

        CartItemDocument item = cart.getItems().stream()
                .filter(i -> Objects.equals(i.getProductId(), productId)
                        && Objects.equals(i.getVariantId(), variantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + productId));

        item.setQuantity(quantity);
        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    /**
     * RemoveItemFromCart (Actor: Buyer).
     * Elimina una línea del carrito propio.
     *
     * Reglas de negocio:
     * - El comprador solo modifica su propio carrito.
     * - Los productos eliminados no forman parte del pedido que se cree
     *   posteriormente.
     */
    public CartDocument removeItem(Long buyerId, String productId, String variantId) {
        CartDocument cart = cartRepository.findByBuyerId(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for buyer: " + buyerId));

        cart.getItems().removeIf(i -> Objects.equals(i.getProductId(), productId)
                && Objects.equals(i.getVariantId(), variantId));

        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    // ==================== Persistence / queries ====================

    public CartDocument getOrCreateCart(Long buyerId) {
        return cartRepository.findByBuyerId(buyerId)
                .orElseGet(() -> {
                    CartDocument cart = new CartDocument();
                    cart.setBuyerId(buyerId);
                    cart.setCreatedAt(LocalDateTime.now());
                    cart.setUpdatedAt(LocalDateTime.now());
                    return cartRepository.save(cart);
                });
    }

    public Optional<CartDocument> findByBuyerId(Long buyerId) {
        return cartRepository.findByBuyerId(buyerId);
    }

    public void clearCart(Long buyerId) {
        cartRepository.deleteByBuyerId(buyerId);
    }

    public BigDecimal getCartSubtotal(Long buyerId) {
        return cartRepository.findByBuyerId(buyerId)
                .map(CartDocument::getSubtotal)
                .orElse(BigDecimal.ZERO);
    }

    public int getTotalItems(Long buyerId) {
        return cartRepository.findByBuyerId(buyerId)
                .map(CartDocument::getTotalItems)
                .orElse(0);
    }

    // ==================== Internal ====================

    /**
     * Valida las reglas del carrito para un producto que se agrega:
     * cantidad > 0, producto existente, apto para nuevas compras y vendedor
     * que pueda participar en el Marketplace.
     */
    private void validateItemForCart(CartItemDocument item) {
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero");
        }

        Long productId = parseProductId(item.getProductId());
        ProductEntity product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    "Product is not available for new purchases: " + item.getProductId());
        }

        if (product.getSeller() == null || product.getSeller().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "The seller of this product cannot participate in the Marketplace");
        }
    }

    private Long parseProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new BusinessRuleException("Product id is required");
        }
        try {
            return Long.parseLong(productId);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Invalid product id: " + productId);
        }
    }
}
