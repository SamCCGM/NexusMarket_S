package application.controller.order;

import application.domain.dto.order.OrderRequest;
import application.domain.dto.order.OrderResponse;
import application.domain.dto.order.OrderItemResponse;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.domain.service.BuyerService;
import application.domain.service.OrderService;
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

    @PostMapping
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody(required = false) OrderRequest request,
            Authentication authentication) {

        // Solo el comprador propietario del carrito puede crear el pedido
        String email = authentication.getName();
        BuyerEntity buyer = buyerService.findByUserEntityEmail(email)
                .orElseThrow(() -> new RuntimeException("Buyer profile not found"));

        if (request != null && request.getBuyerId() != null
                && !buyer.getId().equals(request.getBuyerId())
                && !authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) {
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
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            Authentication authentication,
            Pageable pageable) {
        
        String email = authentication.getName();
        BuyerEntity buyer = buyerService.findByUserEntityEmail(email)
                .orElseThrow(() -> new RuntimeException("Buyer profile not found"));

        Page<OrderEntity> orders = orderService.findByBuyerId(buyer.getId(), pageable);
        return ResponseEntity.ok(orders.map(this::toResponse));
    }

    @GetMapping("/buyer/{buyerId}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Page<OrderResponse>> getOrdersByBuyer(
            @PathVariable Long buyerId,
            Pageable pageable) {
        
        Page<OrderEntity> orders = orderService.findByBuyerId(buyerId, pageable);
        return ResponseEntity.ok(orders.map(this::toResponse));
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
    public ResponseEntity<OrderResponse> payOrder(@PathVariable Long id) {
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