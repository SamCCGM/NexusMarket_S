package application.domain.service;

import application.domain.enums.ProductStatus;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.CartDocument;
import application.persistence.mongodb.document.CartItemDocument;
import application.persistence.mongodb.repository.CartDocumentRepository;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.repository.BuyerRepository;
import application.persistence.mysql.repository.ProductRepository;
import application.persistence.mysql.repository.ProductVariantRepository;
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

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

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
        assertActiveBuyer(buyerId);
        validateItemForCart(item);

        CartDocument cart = getOrCreateCart(buyerId);

        Optional<CartItemDocument> existingItem = cart.getItems().stream()
                .filter(i -> Objects.equals(i.getProductId(), item.getProductId())
                        && Objects.equals(i.getVariantId(), item.getVariantId()))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItemDocument existing = existingItem.get();
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            // El carrito conserva una foto del catalogo, no los valores que
            // envio el cliente: precio, nombre y vendedor se refrescan desde
            // el sistema.
            existing.setProductName(item.getProductName());
            existing.setVariantName(item.getVariantName());
            existing.setPrice(item.getPrice());
            existing.setSellerId(item.getSellerId());
            existing.setSellerName(item.getSellerName());
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
    public CartDocument updateItemQuantity(Long buyerId, Long productId, Long variantId, Integer quantity) {
        assertActiveBuyer(buyerId);
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
        // El producto pudo suspenderse o el vendedor bloquearse desde que se
        // agrego al carrito: actualizar la cantidad tambien revalida la linea.
        validateItemForCart(item);
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
    public CartDocument removeItem(Long buyerId, Long productId, Long variantId) {
        assertActiveBuyer(buyerId);
        CartDocument cart = cartRepository.findByBuyerId(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for buyer: " + buyerId));

        cart.getItems().removeIf(i -> Objects.equals(i.getProductId(), productId)
                && Objects.equals(i.getVariantId(), variantId));

        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    // ==================== Persistence / queries ====================

    public CartDocument getOrCreateCart(Long buyerId) {
        // Un carrito siempre pertenece a un comprador existente y activo:
        // nunca se crea un carrito huerfano.
        assertActiveBuyer(buyerId);
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
        // Vaciar el carrito es una modificacion: un comprador bloqueado no
        // puede realizarla.
        assertActiveBuyer(buyerId);
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

        if (item.getProductId() == null) {
            throw new BusinessRuleException("Product id is required");
        }
        Long productId = item.getProductId();
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

        ProductVariantEntity variant = null;
        if (item.getVariantId() != null) {
            variant = variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant not found: " + item.getVariantId()));
            if (variant.getProduct() == null
                    || !productId.equals(variant.getProduct().getId())) {
                throw new BusinessRuleException(
                        "La variante " + item.getVariantId()
                                + " no pertenece al producto " + productId);
            }
            if (!variant.isActive()) {
                throw new BusinessRuleException(
                        "La variante " + item.getVariantId() + " no esta activa");
            }
        }

        // El precio y los datos descriptivos se toman del catalogo vigente;
        // los valores enviados por el cliente solo se usan como entrada, nunca
        // como fuente de verdad del carrito.
        item.setProductName(product.getName());
        item.setSellerId(product.getSeller().getId());
        item.setSellerName(product.getSeller().getCompanyName());
        if (variant != null) {
            item.setVariantName(variant.getVariantName());
            item.setPrice(variant.getPrice());
        } else {
            item.setPrice(product.getBasePrice());
        }
    }

    /**
     * Un comprador bloqueado no puede iniciar compras ni modificar su carrito.
     */
    private void assertActiveBuyer(Long buyerId) {
        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "El comprador del carrito no existe: " + buyerId));
        if (buyer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Un comprador bloqueado no puede iniciar compras ni modificar su carrito");
        }
    }
}

