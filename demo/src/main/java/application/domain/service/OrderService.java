package application.domain.service;

import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.repository.OrderRepository;
import application.persistence.mysql.repository.OrderItemRepository;
import application.domain.enums.InventoryStatus;
import application.domain.enums.OrderStatus;
import application.domain.enums.ProductStatus;
import application.domain.enums.UserStatus;
import application.domain.ports.PaymentPort;
import application.domain.valueObjects.Money;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.CartDocument;
import application.persistence.mongodb.document.CartItemDocument;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mysql.entity.AddressEmbeddable;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.DigitalProductEntity;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.entity.PhysicalProductEntity;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.entity.ShipmentEntity;
import application.persistence.mysql.repository.BuyerRepository;
import application.persistence.mysql.repository.OrderRepository;
import application.persistence.mysql.repository.OrderItemRepository;
import application.persistence.mysql.repository.ProductRepository;
import application.persistence.mysql.repository.ProductVariantRepository;
import application.persistence.mysql.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicios de dominio de Order (SDD - Order Services).
 *
 * Estados del pedido: PENDING_PAYMENT -> PAID -> DISPATCHED -> DELIVERED -> FINALIZED.
 * Las transiciones no admiten saltos arbitrarios:
 *   - PAID solo se alcanza con el pago confirmado.
 *   - DISPATCHED solo corresponde a pedidos con despacho fisico.
 *   - Los productos virtuales se entregan digitalmente tras confirmar el pago
 *     (no generan envio fisico): PAID -> DELIVERED.
 *   - Un pedido FINALIZED no puede modificarse.
 */
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BuyerRepository buyerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private CartMongoService cartMongoService;

    @Autowired
    private InventoryMongoService inventoryMongoService;

    @Autowired
    private PaymentPort paymentPort;

    // ==================== Domain Services (SDD - Order Services) ====================

    /**
     * CreateOrder (Actor: Buyer / System).
     * Crea un pedido a partir del contenido vigente del carrito, validando
     * productos, cantidades y disponibilidad.
     *
     * Reglas de negocio:
     * - El pedido se crea desde el carrito del comprador; solo incluye los
     *   productos vigentes del carrito con las cantidades acordadas.
     * - Los productos y cantidades se vuelven a validar al crear el pedido:
     *   producto apto para nuevas compras (PUBLISHED), vendedor activo y
     *   cantidad mayor que cero.
     * - Los productos fisicos requieren disponibilidad en el inventario
     *   (suma de existencias disponibles en las bodegas). Si no hay
     *   disponibilidad suficiente, no se crea el pedido.
     * - El pedido inicia en PENDING_PAYMENT.
     * - El precio unitario se toma del sistema (precio de la variante o precio
     *   base del producto), nunca del carrito del cliente.
     */
    public OrderEntity createOrder(Long buyerId, String currency, String notes) {
        if (buyerId == null) {
            throw new BusinessRuleException("Buyer id is required to create an order");
        }

        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found: " + buyerId));

        CartDocument cart = cartMongoService.findByBuyerId(buyerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No se puede crear un pedido: el carrito esta vacio o no existe"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessRuleException("No se puede crear un pedido con un carrito vacio");
        }

        OrderEntity order = new OrderEntity();
        if (order.getItems() == null) {
            order.setItems(new java.util.ArrayList<>());
        }
        order.setBuyer(buyer);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency(currency != null && !currency.isBlank() ? currency.toUpperCase() : "USD");
        order.setNotes(notes);
        if (buyer.getMainAddress() != null) {
            AddressEmbeddable main = buyer.getMainAddress();
            order.setShippingAddress(new AddressEmbeddable(
                    main.getStreet(), main.getCity(), main.getState(), main.getCountry(),
                    main.getPostalCode(), main.getPhoneNumber(), main.isDefault()));
            order.setBillingAddress(new AddressEmbeddable(
                    main.getStreet(), main.getCity(), main.getState(), main.getCountry(),
                    main.getPostalCode(), main.getPhoneNumber(), main.isDefault()));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItemDocument cartItem : cart.getItems()) {
            OrderItemEntity item = buildValidatedItem(order, cartItem);
            order.getItems().add(item);
            subtotal = subtotal.add(item.getTotalPrice());
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(
                order.getTaxAmount() == null ? BigDecimal.ZERO : order.getTaxAmount())
                .add(order.getShippingCost() == null ? BigDecimal.ZERO : order.getShippingCost())
                .subtract(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount()));
        order.setOrderNumber(generateOrderNumber());

        return orderRepository.save(order);
    }

    /**
     * ProcessPayment (Actor: Buyer / System).
     * Registra el resultado del pago. El pedido pasa a PAID solo si el pago
     * se confirma; un intento fallido no debe marcarse como pagado.
     *
     * Reglas de negocio:
     * - La disponibilidad de los productos fisicos se valida de nuevo antes de
     *   confirmar el pago.
     * - El procesamiento del pago delega en el puerto externo (PaymentPort).
     * - Los pedidos 100% virtuales no requieren envio fisico: su entrega
     *   digital ocurre al confirmarse el pago, por lo que pasan a DELIVERED
     *   (DISPATCHED nunca aplica para ellos).
     */
    public OrderEntity processPayment(Long orderId) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException(
                    "Solo un pedido PENDING_PAYMENT puede procesar pago (estado actual: " + order.getStatus() + ")");
        }

        // Revalidacion de disponibilidad antes de confirmar el pago
        for (OrderItemEntity item : order.getItems()) {
            if (requiresPhysical(item.getProduct())) {
                checkAvailability(item.getProduct().getId(),
                        item.getVariant() == null ? null : String.valueOf(item.getVariant().getId()),
                        item.getQuantity(),
                        "No hay unidades disponibles para confirmar el pago del producto "
                                + item.getProduct().getName());
            }
        }

        boolean confirmed = paymentPort.processPayment(orderId,
                Money.builder()
                        .amount(order.getTotalAmount())
                        .currency(order.getCurrency())
                        .build());

        if (!confirmed) {
            throw new BusinessRuleException("El pago no fue confirmado; el pedido permanece PENDING_PAYMENT");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());

        if (!requiresPhysicalDispatch(order)) {
            // Entrega digital inmediata tras confirmar el pago
            order.setStatus(OrderStatus.DELIVERED);
            order.setDeliveredAt(order.getPaidAt());
        }

        return orderRepository.save(order);
    }

    /**
     * DispatchShipment (Actor: LogisticsOperator).
     * Registra la salida del envio hacia su destino y actualiza el pedido
     * asociado a DISPATCHED cuando corresponda.
     *
     * Reglas de negocio:
     * - Solo aplica a pedidos con despacho fisico (DISPATCHED nunca corresponde
     *   a pedidos 100% virtuales).
     * - Solo se despacha un envio creado para un pedido cuyo pago fue confirmado.
     * - La salida de unidades fisicas del almacen se registra como SALE_EXIT.
     * - No se permite despachar el mismo envio dos veces.
     */
    public OrderEntity dispatchShipment(Long orderId) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (!requiresPhysicalDispatch(order)) {
            throw new BusinessRuleException(
                    "El pedido no requiere despacho fisico (sus productos son virtuales)");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException(
                    "El pago del pedido debe estar confirmado (estado actual: " + order.getStatus() + ")");
        }

        ShipmentEntity shipment = shipmentRepository.findByOrderId(orderId).stream()
                .filter(s -> !"DELIVERED".equals(s.getStatus()) && !"DISPATCHED".equals(s.getStatus())
                        && !"IN_TRANSIT".equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un envio pendiente para el pedido " + orderId));

        shipment.setStatus("DISPATCHED");

        // Sale exit de inventario por la venta
        for (OrderItemEntity item : order.getItems()) {
            if (requiresPhysical(item.getProduct())) {
                removeStockForOrder(item.getProduct().getId(),
                        item.getVariant() == null ? null : String.valueOf(item.getVariant().getId()),
                        item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.DISPATCHED);
        order.setShippedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * ConfirmDelivery (Actor: LogisticsOperator).
     * Registra la entrega del envio y actualiza el pedido a DELIVERED.
     * Regla: solo un pedido DISPATCHED puede confirmar su entrega.
     */
    public OrderEntity confirmDelivery(Long orderId) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.DISPATCHED) {
            throw new BusinessRuleException(
                    "Solo un pedido DISPATCHED puede confirmar entrega (estado actual: " + order.getStatus() + ")");
        }

        shipmentRepository.findByOrderId(orderId).stream()
                .filter(s -> "DISPATCHED".equals(s.getStatus()) || "IN_TRANSIT".equals(s.getStatus()))
                .findFirst()
                .ifPresent(s -> {
                    s.setStatus("DELIVERED");
                    s.setActualDeliveryDate(LocalDateTime.now());
                    shipmentRepository.save(s);
                });

        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    /**
     * FinalizeOrder (Actor: System).
     * Cambia el pedido de DELIVERED a FINALIZED cuando se cumple la condicion
     * de finalizacion. Regla: un pedido FINALIZED no puede modificarse y las
     * transiciones deben respetar los estados de OrderStatus.
     */
    public OrderEntity finalizeOrder(Long orderId) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException(
                    "Solo un pedido DELIVERED puede finalizarse (estado actual: " + order.getStatus() + ")");
        }

        order.setStatus(OrderStatus.FINALIZED);
        return orderRepository.save(order);
    }

    // ==================== Persistence / queries ====================

    public OrderEntity save(OrderEntity order) {
        return orderRepository.save(order);
    }

    public Optional<OrderEntity> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<OrderEntity> findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    public List<OrderEntity> findByBuyerId(Long buyerId) {
        return orderRepository.findByBuyerId(buyerId);
    }

    public Page<OrderEntity> findByBuyerId(Long buyerId, Pageable pageable) {
        return orderRepository.findByBuyerId(buyerId, pageable);
    }

    public List<OrderEntity> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    public List<OrderEntity> findByBuyerIdAndStatus(Long buyerId, OrderStatus status) {
        return orderRepository.findByBuyerIdAndStatus(buyerId, status);
    }

    public List<OrderEntity> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByCreatedAtBetween(startDate, endDate);
    }

    public boolean existsByOrderNumber(String orderNumber) {
        return orderRepository.existsByOrderNumber(orderNumber);
    }

    public Page<OrderEntity> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        orderRepository.deleteById(id);
    }

    // ==================== Internal ====================

    private OrderEntity getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /**
     * Valida un item del carrito contra el sistema y construye el OrderItem:
     * producto existente y PUBLISHED, vendedor ACTIVO, cantidad > 0 y
     * disponibilidad en inventario para productos fisicos. El precio se toma
     * del sistema (variante o precio base del producto).
     */
    private OrderItemEntity buildValidatedItem(OrderEntity order, CartItemDocument cartItem) {
        if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
            throw new BusinessRuleException(
                    "Cantidad invalida para el producto del carrito: " + cartItem.getProductId());
        }

        Long productId = parseLong(cartItem.getProductId(), "product id");
        ProductEntity product = productRepository.findByIdWithSeller(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + cartItem.getProductId()));

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    "El producto ya no puede comprarse: " + product.getName());
        }
        if (product.getSeller() == null || product.getSeller().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "El vendedor del producto " + product.getName() + " no puede participar en el Marketplace");
        }

        ProductVariantEntity variant = null;
        if (cartItem.getVariantId() != null && !cartItem.getVariantId().isBlank()) {
            variant = variantRepository.findById(parseLong(cartItem.getVariantId(), "variant id"))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant not found: " + cartItem.getVariantId()));
        }

        String variantIdKey = variant == null ? null : String.valueOf(variant.getId());
        if (requiresPhysical(product)) {
            checkAvailability(productId, variantIdKey, cartItem.getQuantity(),
                    "No hay unidades disponibles para el producto: " + product.getName());
        }

        BigDecimal unitPrice = variant != null && variant.getPrice() != null
                ? variant.getPrice()
                : product.getBasePrice();

        OrderItemEntity item = new OrderItemEntity();
        item.setOrder(order);
        item.setProduct(product);
        item.setVariant(variant);
        item.setQuantity(cartItem.getQuantity());
        item.setUnitPrice(unitPrice);
        item.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        return item;
    }

    /**
     * Verifica que la suma de existencias disponibles (quantity - reserved)
     * en las bodegas cubra la cantidad solicitada. Ignora inventario DAMAGED.
     */
    private void checkAvailability(Long productId, String variantId, Integer quantity, String message) {
        boolean available = inventoryMongoService.findByProductId(productId).stream()
                .filter(i -> Objects.equals(i.getVariantId(), variantId))
                .filter(i -> i.getStatus() != InventoryStatus.DAMAGED)
                .mapToInt(InventoryDocument::getAvailableQuantity)
                .sum() >= quantity;
        if (!available) {
            throw new BusinessRuleException(message);
        }
    }

    /**
     * Sale exit del inventario al despachar: descuenta unidades de manera
     * consistente a traves de las bodegas que contienen el producto.
     */
    private void removeStockForOrder(Long productId, String variantId, Integer quantity) {
        int remaining = quantity;
        List<InventoryDocument> rows = inventoryMongoService.findByProductId(productId).stream()
                .filter(i -> Objects.equals(i.getVariantId(), variantId))
                .sorted(Comparator.comparing(InventoryDocument::getQuantity).reversed())
                .toList();

        for (InventoryDocument row : rows) {
            if (remaining <= 0) {
                break;
            }
            int partial = Math.min(remaining, row.getQuantity());
            if (partial > 0) {
                inventoryMongoService.removeStock(row.getWarehouseId(), productId, variantId, partial);
                remaining -= partial;
            }
        }

        if (remaining > 0) {
            throw new BusinessRuleException(
                    "El inventario ya no cubre la cantidad del pedido (faltan " + remaining + " unidades)");
        }
    }

    private boolean requiresPhysical(ProductEntity product) {
        return product instanceof PhysicalProductEntity;
    }

    private boolean requiresPhysicalDispatch(OrderEntity order) {
        return order.getItems() != null && order.getItems().stream()
                .anyMatch(i -> requiresPhysical(i.getProduct()));
    }

    private Long parseLong(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Invalid " + field + ": (blank)");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Invalid " + field + ": " + value);
        }
    }

    private String generateOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
