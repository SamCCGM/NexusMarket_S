package application.controller.order;

import application.dto.order.OrderRequest;
import application.dto.order.OrderResponse;
import application.dto.order.OrderItemRequest;
import application.dto.order.OrderItemResponse;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.service.BuyerService;
import application.service.OrderService;
import application.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final BuyerService buyerService;
    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest request,
            Authentication authentication) {
        
        // Verify buyer ownership
        String email = authentication.getName();
        BuyerEntity buyer = buyerService.findByUserEntityEmail(email)
                .orElseThrow(() -> new RuntimeException("Buyer profile not found"));
        
        if (!buyer.getId().equals(request.getBuyerId()) && 
            !authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) {
            return ResponseEntity.status(403).build();
        }

        OrderEntity order = new OrderEntity();
        order.setBuyer(buyer);
        order.setStatus(application.domain.enums.OrderStatus.PENDING_PAYMENT);
        order.setCurrency(request.getCurrency());
        order.setNotes(request.getNotes());
        
        // Set addresses from JSON
        // In a real implementation, parse JSON and set AddressEmbeddable
        
        BigDecimal subtotal = BigDecimal.ZERO;
        
        for (OrderItemRequest itemReq : request.getItems()) {
            ProductEntity product = productService.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemReq.getProductId()));
            
            ProductVariantEntity variant = null;
            if (itemReq.getVariantId() != null) {
                variant = productService.findVariantById(itemReq.getVariantId())
                        .orElseThrow(() -> new RuntimeException("Variant not found: " + itemReq.getVariantId()));
            }

            OrderItemEntity item = new OrderItemEntity();
            item.setOrder(order);
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setTotalPrice(itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            
            order.getItems().add(item);
            subtotal = subtotal.add(item.getTotalPrice());
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal); // Add tax, shipping, discount calculations here
        
        OrderEntity saved = orderService.save(order);
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

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR') or hasRole('LOGISTICS_OPERATOR')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        
        return orderService.findById(id)
                .map(order -> {
                    order.setStatus(application.domain.enums.OrderStatus.valueOf(status.toUpperCase()));
                    
                    if (application.domain.enums.OrderStatus.PAID.equals(order.getStatus())) {
                        order.setPaidAt(LocalDateTime.now());
                    } else if (application.domain.enums.OrderStatus.DISPATCHED.equals(order.getStatus())) {
                        order.setShippedAt(LocalDateTime.now());
                    } else if (application.domain.enums.OrderStatus.DELIVERED.equals(order.getStatus())) {
                        order.setDeliveredAt(LocalDateTime.now());
                    }
                    
                    OrderEntity updated = orderService.save(order);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
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