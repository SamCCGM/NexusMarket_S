package application.controller.cart;

import application.dto.cart.CartItemRequest;
import application.dto.cart.CartResponse;
import application.dto.cart.CartSummaryResponse;
import application.persistence.mongodb.document.CartDocument;
import application.persistence.mongodb.document.CartItemDocument;
import application.service.BuyerService;
import application.service.CartMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartMongoService cartService;
    private final BuyerService buyerService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        Long buyerId = getBuyerIdFromAuth(authentication);
        return cartService.findByBuyerId(buyerId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(CartResponse.empty()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestBody CartItemRequest request,
            Authentication authentication) {
        
        Long buyerId = getBuyerIdFromAuth(authentication);
        
        CartItemDocument item = CartItemDocument.builder()
                .productId(request.getProductId())
                .variantId(request.getVariantId())
                .productName(request.getProductName())
                .variantName(request.getVariantName())
                .productImageUrl(request.getProductImageUrl())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .sellerId(request.getSellerId())
                .sellerName(request.getSellerName())
                .build();

        CartDocument cart = cartService.addItem(buyerId, item);
        return ResponseEntity.ok(toResponse(cart));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable String productId,
            @RequestParam(required = false) String variantId,
            @RequestParam Integer quantity,
            Authentication authentication) {
        
        Long buyerId = getBuyerIdFromAuth(authentication);
        CartDocument cart = cartService.updateItemQuantity(buyerId, productId, variantId, quantity);
        return ResponseEntity.ok(toResponse(cart));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable String productId,
            @RequestParam(required = false) String variantId,
            Authentication authentication) {
        
        Long buyerId = getBuyerIdFromAuth(authentication);
        CartDocument cart = cartService.removeItem(buyerId, productId, variantId);
        return ResponseEntity.ok(toResponse(cart));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        Long buyerId = getBuyerIdFromAuth(authentication);
        cartService.clearCart(buyerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<CartSummaryResponse> getCartSummary(Authentication authentication) {
        Long buyerId = getBuyerIdFromAuth(authentication);
        BigDecimal subtotal = cartService.getCartSubtotal(buyerId);
        int totalItems = cartService.getTotalItems(buyerId);
        
        return ResponseEntity.ok(CartSummaryResponse.builder()
                .subtotal(subtotal)
                .totalItems(totalItems)
                .build());
    }

    private Long getBuyerIdFromAuth(Authentication authentication) {
        // El usuario autenticado se identifica por su correo; se resuelve el
        // perfil de comprador para obtener su id y acotar el carrito propio.
        String email = authentication.getName();
        return buyerService.findByUserEntityEmail(email)
                .orElseThrow(() -> new RuntimeException("Buyer profile not found"))
                .getId();
    }

    private CartResponse toResponse(CartDocument cart) {
        return CartResponse.builder()
                .id(cart.getId())
                .buyerId(cart.getBuyerId())
                .items(cart.getItems() != null ? 
                        cart.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()) : null)
                .subtotal(cart.getSubtotal())
                .totalItems(cart.getTotalItems())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartResponse.CartItemResponse toItemResponse(CartItemDocument item) {
        return CartResponse.CartItemResponse.builder()
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .productImageUrl(item.getProductImageUrl())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .totalPrice(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .sellerId(item.getSellerId())
                .sellerName(item.getSellerName())
                .build();
    }
}