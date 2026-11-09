package application.controller.order;

import application.domain.dto.order.OrderRequest;
import application.domain.dto.order.OrderResponse;
import application.domain.dto.order.OrderItemResponse;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.entity.SellerEntity;
import application.domain.service.BuyerService;
import application.domain.service.OrderService;
import application.domain.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final BuyerService buyerService;
    private final SellerService sellerService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody(required = false) @Valid OrderRequest request,
            Authentication authentication) {

        // Solo el comprador propietario del carrito puede crear el pedido:
        // el buyerId nunca se toma del cliente.
        String email = authentication.getName();
        BuyerEntity buyer = buyerService.findByUserEntityEmail(email).orElse(null);
        if (buyer == null) {
            return ResponseEntity.status(403).build();
        }

        // CreateOrder (SDD): el pedido se crea a partir del contenido vigente
        // del carrito revalidando productos, cantidades y disponibilidad.
        OrderEntity saved = orderService.createOrder(
                buyer.getId(),
                request != null ? request.getCurrency() : null,
                request != null ? request.getNotes() : null);
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id, Authentication authentication) {
        OrderEntity order = orderService.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessOrder(order, authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(toResponse(order));
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            Authentication authentication,
            Pageable pageable) {
        
        String email = authentication.getName();
        BuyerEntity buyer = buyerService.findByUserEntityEmail(email).orElse(null);
        if (buyer == null) {
            return ResponseEntity.status(403).build();
        }

        Page<OrderEntity> orders = orderService.findByBuyerId(buyer.getId(), pageable);
        return ResponseEntity.ok(orders.map(this::toResponse));
    }

    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Page<OrderResponse>> getOrdersByBuyer(
            @PathVariable Long buyerId,
            Pageable pageable,
            Authentication authentication) {

        if (isAdmin(authentication)) {
            return ResponseEntity.ok(orderService.findByBuyerId(buyerId, pageable).map(this::toResponse));
        }

        // Un vendedor solo puede ver los pedidos del comprador que contienen
        // productos suyos, no el historial completo del comprador.
        SellerEntity seller = sellerService.findByUserEntityEmail(authentication.getName())
                .orElseThrow(() -> new application.exception.BusinessRuleException(
                        "El perfil de vendedor no existe para: " + authentication.getName()));
        return ResponseEntity.ok(orderService
                .findByBuyerIdAndSellerId(buyerId, seller.getId(), pageable)
                .map(this::toResponse));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        Page<OrderEntity> orders;
        if (status != null) {
            orders = orderService.findByStatus(
                    application.domain.enums.OrderStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            orders = orderService.findAll(pageable);
        }
        
        return ResponseEntity.ok(orders.map(this::toResponse));
    }

    /**
     * ProcessPayment (SDD): el pedido pasa a PAID solo si el pago se confirma.
     */
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> payOrder(@PathVariable Long id, Authentication authentication) {
        OrderEntity order = orderService.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(authentication) && !isBuyerOwner(order, authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(toResponse(orderService.processPayment(id)));
    }

    /**
     * DispatchShipment (SDD): registra la salida del envio y actualiza el
     * pedido a DISPATCHED. Solo aplica a pedidos con despacho fisico pagado.
     */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> dispatchOrder(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orderService.dispatchShipment(id)));
    }

    /**
     * ConfirmDelivery (SDD): registra la entrega y actualiza el pedido a DELIVERED.
     */
    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasRole('LOGISTICS_OPERATOR') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> confirmDelivery(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orderService.confirmDelivery(id)));
    }

    /**
     * FinalizeOrder (SDD): DELIVERED -> FINALIZED. Un pedido FINALIZED no puede
     * modificarse; no se expone ninguna operacion de cambio de estado arbitrario.
     */
    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> finalizeOrder(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(orderService.finalizeOrder(id)));
    }

    /**
     * CancelOrder: el comprador cancela su pedido aun no pagado y el sistema
     * libera la reserva de inventario (complemento de ReserveStock). Un pedido
     * pagado no se cancela: se gestiona con devolucion/reembolso.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            Authentication authentication) {

        OrderEntity order = orderService.findById(id)
                .orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));
        boolean isOwner = order.getBuyer() != null
                && authentication.getName().equals(order.getBuyer().getEmail());
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toResponse(orderService.cancelOrder(id, reason)));
    }

    /**
     * DeleteOrder: solo un administrador puede eliminar un pedido y solo si
     * no tiene efectos (CANCELLED). Los pedidos pagados/despachados/entregados
     * conservan su trazabilidad.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GenerateInvoice (SDD): genera la informacion de facturacion del pedido;
     * solo con pago confirmado. La factura es un resultado asociado al pedido.
     */
    @GetMapping("/{id}/invoice")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<application.domain.dto.billing.InvoiceResponse> generateInvoice(
            @PathVariable Long id,
            Authentication authentication) {

        OrderEntity order = orderService.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAdmin(authentication) && !isBuyerOwner(order, authentication)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(orderService.generateInvoice(id));
    }

    /**
     * Reglas de visibilidad de un pedido:
     * - Administrador y operador logistico: acceso operativo.
     * - Comprador: solo sus propios pedidos.
     * - Vendedor: solo pedidos que contienen alguno de sus productos.
     */
    private boolean canAccessOrder(OrderEntity order, Authentication authentication) {
        if (isAdmin(authentication) || hasRole(authentication, "LOGISTICS_OPERATOR")) {
            return true;
        }
        if (hasRole(authentication, "BUYER")) {
            return isBuyerOwner(order, authentication);
        }
        if (hasRole(authentication, "SELLER")) {
            return sellerService.findByUserEntityEmail(authentication.getName())
                    .map(seller -> order.getItems() != null && order.getItems().stream()
                            .anyMatch(item -> item.getProduct() != null
                                    && item.getProduct().getSeller() != null
                                    && seller.getId().equals(item.getProduct().getSeller().getId())))
                    .orElse(false);
        }
        return false;
    }

    private boolean isBuyerOwner(OrderEntity order, Authentication authentication) {
        return authentication != null
                && order.getBuyer() != null
                && authentication.getName().equals(order.getBuyer().getEmail());
    }

    private boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ADMINISTRATOR");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private OrderResponse toResponse(OrderEntity entity) {
        return OrderResponse.builder()
                .id(entity.getId())
                .orderNumber(entity.getOrderNumber())
                .buyerId(entity.getBuyer().getId())
                .buyerName(entity.getBuyer().getFullName())
                .buyerEmail(entity.getBuyer().getEmail())
                .status(entity.getStatus().name())
                .subtotal(entity.getSubtotal())
                .taxAmount(entity.getTaxAmount())
                .shippingCost(entity.getShippingCost())
                .discountAmount(entity.getDiscountAmount())
                .totalAmount(entity.getTotalAmount())
                .currency(entity.getCurrency())
                .shippingAddress(entity.getShippingAddress() != null ? entity.getShippingAddress().toString() : null)
                .billingAddress(entity.getBillingAddress() != null ? entity.getBillingAddress().toString() : null)
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .paidAt(entity.getPaidAt())
                .shippedAt(entity.getShippedAt())
                .deliveredAt(entity.getDeliveredAt())
                .items(entity.getItems() != null ? 
                        entity.getItems().stream().map(this::toItemResponse).collect(Collectors.toList()) : null)
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItemEntity item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .variantName(item.getVariant() != null ? item.getVariant().getVariantName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .build();
    }
}