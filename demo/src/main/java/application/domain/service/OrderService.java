package application.domain.service;

import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.repository.OrderRepository;
import application.persistence.mysql.repository.OrderItemRepository;
import application.domain.enums.InventoryStatus;
import application.domain.enums.OrderStatus;
import application.domain.enums.ProductStatus;
import application.domain.enums.UserStatus;
import application.domain.ports.NotificationPort;
import application.domain.ports.PaymentPort;
import application.domain.valueObjects.Money;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mongodb.document.CartDocument;
import application.persistence.mongodb.document.CartItemDocument;
import application.persistence.mongodb.document.InventoryDocument;
import application.persistence.mongodb.document.StockReservationDocument;
import application.persistence.mysql.entity.AddressEmbeddable;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.DigitalProductEntity;
import application.persistence.mysql.entity.LogisticsOperatorEntity;
import application.persistence.mysql.entity.PhysicalProductEntity;
import application.persistence.mysql.entity.ProductEntity;
import application.persistence.mysql.entity.ProductVariantEntity;
import application.persistence.mysql.entity.ShipmentEntity;
import application.persistence.mysql.entity.WarehouseEntity;
import application.persistence.mysql.repository.BuyerRepository;
import application.persistence.mysql.repository.LogisticsOperatorRepository;
import application.persistence.mysql.repository.WarehouseRepository;
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
    private StockReservationService stockReservationService;

    @Autowired
    private PaymentPort paymentPort;

    @Autowired
    private LogisticsOperatorRepository logisticsOperatorRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private application.domain.ports.ShipmentPort shipmentPort;

    @Autowired
    private NotificationPort notificationPort;

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
     * - ReserveStock: al confirmar la creacion del pedido se reservan las
     *   unidades fisicas (hold pre-pago); la reserva se consume como
     *   SALE_EXIT al despachar y evita sobreventa mientras el pago esta
     *   pendiente.
     * - El pedido inicia en PENDING_PAYMENT.
     * - El precio unitario se toma del sistema (precio de la variante o precio
     *   base del producto), nunca del carrito del cliente.
     * - Una vez creado el pedido las unidades salen del carrito (ClearCart):
     *   el pedido ya formalizo la compra de su contenido vigente.
     */
    public OrderEntity createOrder(Long buyerId, String currency, String notes) {
        if (buyerId == null) {
            throw new BusinessRuleException("Buyer id is required to create an order");
        }

        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found: " + buyerId));

        // Un comprador bloqueado no inicia compras (SDD - ProcessPayment/Buyer).
        assertBuyerCanOperate(buyer);

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

        // Un pedido con productos fisicos no puede quedar sin destino de envio.
        if (requiresPhysicalDispatch(order) && !hasCompleteShippingAddress(buyer.getMainAddress())) {
            throw new BusinessRuleException(
                    "Los pedidos con productos fisicos requieren una direccion de envio completa "
                            + "(calle, ciudad y pais) en el perfil del comprador");
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(
                order.getTaxAmount() == null ? BigDecimal.ZERO : order.getTaxAmount())
                .add(order.getShippingCost() == null ? BigDecimal.ZERO : order.getShippingCost())
                .subtract(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount()));
        order.setOrderNumber(generateOrderNumber());

        OrderEntity saved = orderRepository.save(order);

        // ReserveStock: hold pre-pago de las unidades fisicas del pedido.
        // Cada asignacion (pedido + linea + bodega) queda registrada en
        // Mongo para que cancelacion/despacho operen solo sobre SUS reservas.
        // MongoDB no participa de la transaccion JPA: si una reserva posterior
        // falla, se liberan las reservas ya hechas para no dejar unidades
        // retenidas por un pedido que finalmente no se crea.
        List<StockReservationDocument> reservations = new java.util.ArrayList<>();
        try {
            for (OrderItemEntity item : saved.getItems()) {
                if (requiresPhysical(item.getProduct())) {
                    reserveStockForOrder(
                            saved.getId(),
                            item.getId(),
                            item.getProduct().getId(),
                            item.getVariant() == null ? null : item.getVariant().getId(),
                            item.getQuantity(),
                            item.getProduct().getName(),
                            reservations);
                }
            }

            // ClearCart: el pedido formalizo la compra del contenido vigente del carrito.
            cartMongoService.clearCart(buyerId);
        } catch (RuntimeException ex) {
            // Compensacion exacta: se liberan las mismas filas reservadas y se
            // eliminan los registros de la creacion fallida. Nunca se tocan
            // reservas de otros pedidos.
            for (int i = reservations.size() - 1; i >= 0; i--) {
                StockReservationDocument reservation = reservations.get(i);
                try {
                    inventoryMongoService.releaseStock(
                            reservation.getWarehouseId(), reservation.getProductId(),
                            reservation.getVariantId(), reservation.getQuantity());
                    stockReservationService.deleteById(reservation.getId());
                } catch (RuntimeException ignored) {
                    // Se preserva la excepcion original; el registro ACTIVE
                    // queda disponible para diagnostico si la liberacion fallo.
                }
            }
            throw ex;
        }

        notificationPort.notifyOrderEvent(saved.getId(),
                "Pedido creado: " + saved.getOrderNumber() + " (queda PENDING_PAYMENT)");

        return saved;
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

        // Un comprador bloqueado no puede confirmar la compra.
        assertBuyerCanOperate(order.getBuyer());

        // Revalidacion antes de confirmar el pago: el pedido ya mantiene una
        // reserva (ReserveStock), por lo que no se puede volver a restar
        // reservedQuantity: eso descontaria dos veces las unidades del propio
        // pedido y haria fallar el pago cuando consume mas de la mitad del stock.
        for (OrderItemEntity item : order.getItems()) {
            if (requiresPhysical(item.getProduct())) {
                checkPaymentAvailability(item,
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

        // Sale exit de inventario por la venta: consume exactamente las
        // reservas creadas para ESTE pedido, no reservas agregadas de otros.
        consumeReservationsForOrder(order);

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
        OrderEntity saved = orderRepository.save(order);
        notificationPort.notifyOrderEvent(orderId, "Pedido finalizado: " + saved.getOrderNumber());
        return saved;
    }

    /**
     * CancelOrder / Expiration (Actor: Buyer / System).
     * Cancela un pedido que aun no fue pagado y libera la reserva de
     * inventario hecha por ReserveStock al crear el pedido.
     *
     * Reglas de negocio:
     * - Solo un pedido PENDING_PAYMENT puede cancelarse (uno pagado requiere
     *   el flujo de devolucion/reembolso, no una cancelacion).
     * - La cancelacion libera exactamente las unidades reservadas de los
     *   productos fisicos, sin tocar las existencias totales.
     * - El pedido pasa al estado terminal CANCELLED (no admite pago posterior).
     */
    public OrderEntity cancelOrder(Long orderId, String reason) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException(
                    "Solo un pedido PENDING_PAYMENT puede cancelarse (estado actual: " + order.getStatus() + ")");
        }

        // Libera exclusivamente las reservas registradas para este pedido.
        // La bodega puede estar inactiva: liberar un compromiso ya existente
        // debe funcionar igual (InventoryMongoService.releaseStock lo permite).
        releaseReservationsForOrder(order);

        order.setStatus(OrderStatus.CANCELLED);
        OrderEntity saved = orderRepository.save(order);
        notificationPort.notifyOrderEvent(orderId,
                "Pedido cancelado" + (reason != null && !reason.isBlank() ? ": " + reason : "")
                        + " (" + saved.getOrderNumber() + ")");
        return saved;
    }

    // ==================== Domain Services (SDD - Shipment/Billing Services) ====================

    /**
     * CreateShipment (Actor: LogisticsOperator / System).
     * Crea un envio para un pedido fisico pagado que necesita despacho.
     *
     * Reglas de negocio:
     * - El envio se asocia a un pedido valido que incluya productos fisicos
     *   (los digitales se entregan digitalmente y no generan envio).
     * - El pedido debe tener el pago confirmado.
     * - El envio debe tener un destino valido (direccion de envio del pedido).
     * - No se crean envios duplicados para pedidos con envio en curso.
     */
    public ShipmentEntity createShipment(Long orderId, Long logisticsOperatorId, Long warehouseId,
                                         String carrier, Integer estimatedDays) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (!requiresPhysicalDispatch(order)) {
            throw new BusinessRuleException(
                    "El pedido no incluye productos fisicos; no requiere envio de mercancia");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException(
                    "Solo se crea envio para un pedido con el pago confirmado (estado actual: " + order.getStatus() + ")");
        }
        if (order.getShippingAddress() == null || isBlank(order.getShippingAddress().getStreet())
                || isBlank(order.getShippingAddress().getCity()) || isBlank(order.getShippingAddress().getCountry())) {
            throw new BusinessRuleException(
                    "El envio debe tener un destino valido; el pedido no registra direccion de envio completa");
        }

        boolean shipmentInCourse = shipmentRepository.findByOrderId(orderId).stream()
                .anyMatch(s -> !"DELIVERED".equals(s.getStatus()));
        if (shipmentInCourse) {
            throw new BusinessRuleException(
                    "El pedido ya tiene un envio en curso; no se puede crear otro");
        }

        ShipmentEntity shipment = new ShipmentEntity();
        shipment.setTrackingNumber(generateTrackingNumber());
        shipment.setOrder(order);
        if (logisticsOperatorId != null) {
            shipment.setLogisticsOperator(logisticsOperatorRepository.findById(logisticsOperatorId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Logistics operator not found: " + logisticsOperatorId)));
        }
        if (warehouseId != null) {
            shipment.setWarehouse(warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId)));
        }
        shipment.setCarrier(carrier);
        shipment.setStatus("CREATED");
        if (estimatedDays != null && estimatedDays > 0) {
            shipment.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(estimatedDays));
        }
        shipment.setDestinationAddress(order.getShippingAddress());

        // Outputs Ports (SDD): la solicitud de despacho externo pasa por el
        // puerto; se conserva el identificador externo del envio.
        shipment.setExternalShipmentId(shipmentPort.createShipment(
                orderId,
                application.domain.valueObjects.Address.builder()
                        .value(order.getShippingAddress().getStreet() + ", "
                                + order.getShippingAddress().getCity() + ", "
                                + order.getShippingAddress().getCountry())
                        .build()));

        ShipmentEntity saved = shipmentRepository.save(shipment);
        notificationPort.notifyOrderEvent(orderId, "Envio creado: " + saved.getTrackingNumber());
        return saved;
    }

    /**
     * GenerateInvoice (Actor: System).
     * Genera la informacion de facturacion correspondiente a un pedido con
     * pago confirmado. La factura es un RESULTADO asociado al pedido (SDD):
     * los importes corresponden exactamente a los registrados en el pedido y
     * no se genera si el pago esta pendiente o fallido.
     */
    public application.domain.dto.billing.InvoiceResponse generateInvoice(Long orderId) {
        OrderEntity order = getOrderOrThrow(orderId);

        if (order.getPaidAt() == null) {
            throw new BusinessRuleException(
                    "No se genera factura sin pago confirmado (estado actual: " + order.getStatus() + ")");
        }

        List<application.domain.dto.billing.InvoiceResponse.ItemLine> lines = order.getItems().stream()
                .map(item -> application.domain.dto.billing.InvoiceResponse.ItemLine.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .variantName(item.getVariant() != null ? item.getVariant().getVariantName() : null)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return application.domain.dto.billing.InvoiceResponse.builder()
                .invoiceNumber("INV-" + order.getOrderNumber())
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus().name())
                .buyerId(order.getBuyer().getId())
                .buyerName(order.getBuyer().getFullName())
                .buyerEmail(order.getBuyer().getEmail())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(lines)
                .orderCreatedAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .issuedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Expira pedidos PENDING_PAYMENT que superaron la ventana de pago.
     * Actor: System. Cancela y libera la reserva de inventario asociada.
     */
    public int expireUnpaidOrders(int hours) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusHours(hours);
        List<OrderEntity> unpaid = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);

        int expired = 0;
        for (OrderEntity order : unpaid) {
            try {
                cancelOrder(order.getId(), "Pedido expirado sin pago");
                expired++;
            } catch (RuntimeException e) {
                notificationPort.notifyOrderEvent(order.getId(),
                        "No se pudo expirar el pedido: " + e.getMessage());
            }
        }
        return expired;
    }

    // ==================== Persistence / queries ====================

    public OrderEntity save(OrderEntity order) {
        return orderRepository.save(order);
    }

    public Optional<OrderEntity> findById(Long id) {
        return orderRepository.findByIdWithDetails(id);
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

    /**
     * Pedidos de un comprador que contienen al menos un producto del vendedor.
     * Evita que un vendedor vea el historial completo de compras del comprador.
     */
    public Page<OrderEntity> findByBuyerIdAndSellerId(Long buyerId, Long sellerId, Pageable pageable) {
        return orderRepository.findByBuyerIdAndSellerId(buyerId, sellerId, pageable);
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
        // La factura (y el resto de la trazabilidad) se reconstruye desde el
        // pedido: solo se borran pedidos cancelados, que no generaron efectos.
        OrderEntity order = getOrderOrThrow(id);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Solo puede eliminarse un pedido CANCELLED; los pedidos con efectos "
                            + "(pagados, despachados o entregados) conservan su trazabilidad (estado actual: "
                            + order.getStatus() + ")");
        }
        orderRepository.deleteById(id);
        // El pedido cancelado ya no existe: sus reservas (RELEASED) dejan de
        // tener sentido como trazabilidad historica y se eliminan tambien.
        stockReservationService.deleteByOrder(id);
    }

    // ==================== Internal ====================

    private OrderEntity getOrderOrThrow(Long orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /**
     * El comprador debe estar activo para operar: iniciando compras,
     * confirmando pagos o usando el carrito (SDD: un comprador bloqueado
     * no puede iniciar compras).
     */
    private void assertBuyerCanOperate(BuyerEntity buyer) {
        if (buyer == null || buyer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Un comprador bloqueado no puede realizar compras");
        }
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

        Long productId = cartItem.getProductId();
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
        if (cartItem.getVariantId() != null) {
            variant = variantRepository.findById(cartItem.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant not found: " + cartItem.getVariantId()));
            if (variant.getProduct() == null
                    || !productId.equals(variant.getProduct().getId())) {
                throw new BusinessRuleException(
                        "La variante " + cartItem.getVariantId()
                                + " no pertenece al producto " + productId);
            }
            if (!variant.isActive()) {
                throw new BusinessRuleException(
                        "La variante " + cartItem.getVariantId() + " no esta activa");
            }
        }

        Long variantIdKey = variant == null ? null : variant.getId();
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
    private void checkAvailability(Long productId, Long variantId, Integer quantity, String message) {
        boolean available = inventoryMongoService.findByProductId(productId).stream()
                .filter(i -> Objects.equals(i.getVariantId(), variantId))
                .filter(i -> i.getStatus() != InventoryStatus.DAMAGED)
                .filter(i -> isWarehouseOperable(i.getWarehouseId()))
                .mapToInt(InventoryDocument::getAvailableQuantity)
                .sum() >= quantity;
        if (!available) {
            throw new BusinessRuleException(message);
        }
    }

    /**
     * Revalidacion de disponibilidad para un pedido que YA tiene una reserva
     * hecha por ReserveStock. El stock vendible es quantity - damaged (las
     * unidades reservadas siguen fisicamente en la bodega y pertenecen a algun
     * pedido pendiente, incluido el que se esta pagando). Ademas se comprueba
     * que la reserva trazable de ESTA linea siga activa.
     */
    private void checkPaymentAvailability(OrderItemEntity item, String message) {
        Long productId = item.getProduct().getId();
        Long variantId = item.getVariant() == null ? null : item.getVariant().getId();

        List<StockReservationDocument> activeReservations =
                stockReservationService.findActiveByOrderItem(item.getId());
        int activeReserved = activeReservations.stream()
                .mapToInt(StockReservationDocument::getQuantity)
                .sum();
        if (activeReserved < item.getQuantity()) {
            throw new BusinessRuleException(message + " (reserva del pedido incompleta: "
                    + activeReserved + " de " + item.getQuantity() + " unidades)");
        }

        // Se valida cada asignacion contra la fila de inventario concreta:
        // una reserva no queda cubierta por stock de otra bodega.
        for (StockReservationDocument reservation : activeReservations) {
            InventoryDocument row = inventoryMongoService
                    .findByWarehouseIdAndProductIdAndVariantId(
                            reservation.getWarehouseId(), productId, variantId)
                    .orElseThrow(() -> new BusinessRuleException(message));
            int sellable = zeroIfNull(row.getQuantity()) - zeroIfNull(row.getDamagedQuantity());
            if (sellable < reservation.getQuantity()) {
                throw new BusinessRuleException(message);
            }
        }
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Solo las bodegas activas pueden aportar stock a pedidos nuevos. Las
     * bodegas inactivas conservan sus filas para liberar o consumir reservas
     * ya comprometidas, pero no deben bloquear una compra con stock disponible
     * en otras bodegas ni contarse como disponibilidad vendible.
     */
    private boolean isWarehouseOperable(Long warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        return warehouseRepository.findById(warehouseId)
                .map(WarehouseEntity::isActive)
                .orElse(false);
    }

    /**
     * ReserveStock al crear el pedido: reserva unidades de manera consistente
     * a traves de las bodegas disponibles (cantidad disponible desc) y registra
     * cada asignacion con el pedido y la linea que la origino.
     */
    private void reserveStockForOrder(Long orderId, Long orderItemId, Long productId, Long variantId,
                                      Integer quantity, String productName,
                                      List<StockReservationDocument> reservations) {
        int remaining = quantity;
        List<InventoryDocument> rows = inventoryMongoService.findByProductId(productId).stream()
                .filter(i -> Objects.equals(i.getVariantId(), variantId))
                .filter(i -> i.getStatus() != InventoryStatus.DAMAGED)
                .filter(i -> isWarehouseOperable(i.getWarehouseId()))
                .sorted(Comparator.comparingInt(InventoryDocument::getAvailableQuantity).reversed())
                .toList();

        for (InventoryDocument row : rows) {
            if (remaining <= 0) {
                break;
            }
            int partial = Math.min(remaining, row.getAvailableQuantity());
            if (partial > 0) {
                inventoryMongoService.reserveStock(row.getWarehouseId(), productId, variantId, partial);
                try {
                    reservations.add(stockReservationService.create(
                            orderId, orderItemId, row.getWarehouseId(), productId, variantId, partial));
                } catch (RuntimeException ex) {
                    // El agregado ya se incremento, pero el registro trazable
                    // no pudo guardarse: se revierte esa asignacion puntual.
                    inventoryMongoService.releaseStock(
                            row.getWarehouseId(), productId, variantId, partial);
                    throw ex;
                }
                remaining -= partial;
            }
        }

        if (remaining > 0) {
            throw new BusinessRuleException(
                    "No hay disponibilidad para reservar el producto " + productName
                            + " (faltan " + remaining + " unidades)");
        }
    }

    /**
     * Sale exit del inventario al despachar. Consume exactamente las reservas
     * ACTIVE asociadas a cada linea del pedido, de modo que nunca descuenta
     * unidades retenidas por otro pedido.
     */
    private void consumeReservationsForOrder(OrderEntity order) {
        List<StockReservationDocument> active = stockReservationService.findActiveByOrder(order.getId());

        for (OrderItemEntity item : order.getItems()) {
            if (!requiresPhysical(item.getProduct())) {
                continue;
            }
            int reserved = active.stream()
                    .filter(r -> Objects.equals(r.getOrderItemId(), item.getId()))
                    .mapToInt(StockReservationDocument::getQuantity)
                    .sum();
            if (reserved != item.getQuantity()) {
                throw new BusinessRuleException(
                        "Las reservas del pedido no coinciden con la linea " + item.getId()
                                + " (reservado: " + reserved + ", pedido: " + item.getQuantity() + ")");
            }
        }

        // Se valida todo antes de tocar inventario para no dejar un despacho a
        // medias si una fila ya no cubre la reserva.
        for (StockReservationDocument reservation : active) {
            InventoryDocument row = inventoryMongoService
                    .findByWarehouseIdAndProductIdAndVariantId(
                            reservation.getWarehouseId(), reservation.getProductId(), reservation.getVariantId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "No existe inventario para consumir la reserva " + reservation.getId()));
            int sellable = zeroIfNull(row.getQuantity()) - zeroIfNull(row.getDamagedQuantity());
            if (sellable < reservation.getQuantity()) {
                throw new BusinessRuleException(
                        "El inventario ya no cubre la reserva del pedido (faltan "
                                + (reservation.getQuantity() - sellable) + " unidades)");
            }
        }

        for (StockReservationDocument reservation : active) {
            inventoryMongoService.removeStock(
                    reservation.getWarehouseId(), reservation.getProductId(),
                    reservation.getVariantId(), reservation.getQuantity());
            stockReservationService.markConsumed(reservation);
        }
    }

    /**
     * Libera las reservas ACTIVE del pedido al cancelarlo. Si una reserva no
     * puede liberarse, se conserva ACTIVE para diagnostico y se notifica; no se
     * libera ninguna reserva agregada de otros pedidos.
     */
    private void releaseReservationsForOrder(OrderEntity order) {
        List<StockReservationDocument> active = stockReservationService.findActiveByOrder(order.getId());

        if (active.isEmpty() && requiresPhysicalDispatch(order)) {
            notificationPort.notifyOrderEvent(order.getId(),
                    "Pedido fisico sin reservas trazables al cancelar; no se libero stock de otros pedidos");
            return;
        }

        for (StockReservationDocument reservation : active) {
            try {
                inventoryMongoService.releaseStock(
                        reservation.getWarehouseId(), reservation.getProductId(),
                        reservation.getVariantId(), reservation.getQuantity());
                stockReservationService.markReleased(reservation);
            } catch (RuntimeException ex) {
                notificationPort.notifyOrderEvent(order.getId(),
                        "No se pudo liberar la reserva " + reservation.getId()
                                + " del pedido; queda pendiente de revision");
            }
        }
    }

    private boolean requiresPhysical(ProductEntity product) {
        // El producto puede ser un proxy de Hibernate: se resuelve su tipo real.
        ProductEntity real = (ProductEntity) org.hibernate.Hibernate.unproxy(product);
        return real instanceof PhysicalProductEntity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasCompleteShippingAddress(AddressEmbeddable address) {
        return address != null
                && !isBlank(address.getStreet())
                && !isBlank(address.getCity())
                && !isBlank(address.getCountry());
    }

    private boolean requiresPhysicalDispatch(OrderEntity order) {
        return order.getItems() != null && order.getItems().stream()
                .anyMatch(i -> requiresPhysical(i.getProduct()));
    }

    private String generateOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }

    private String generateTrackingNumber() {
        return "SHP-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }
}



