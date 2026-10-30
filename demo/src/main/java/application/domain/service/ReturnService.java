package application.domain.service;

import application.domain.dto.returns.ReturnRequest;
import application.domain.dto.returns.ReturnResponse;
import application.domain.enums.OrderStatus;
import application.domain.ports.NotificationPort;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.entity.OrderItemEntity;
import application.persistence.mysql.entity.ReturnEntity;
import application.persistence.mysql.repository.OrderItemRepository;
import application.persistence.mysql.repository.OrderRepository;
import application.persistence.mysql.repository.ReturnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicios de dominio de Return (SDD - Return Services).
 *
 * Estados de la devolucion: REQUESTED -> APPROVED.
 * Regla clave: la aprobacion de una devolucion NO devuelve las unidades al
 * inventario automaticamente; las unidades solo se reincorporan mediante
 * ReturnStock cuando se han recibido y revisado.
 */
@Service
@Transactional
public class ReturnService {

    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_APPROVED = "APPROVED";

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private BuyerService buyerService;

    @Autowired
    private NotificationPort notificationPort;

    /**
     * RequestReturn (Actor: Buyer).
     * Registra una solicitud de devolucion asociada a una compra del comprador.
     *
     * Reglas de negocio:
     * - La devolucion debe estar relacionada con un pedido existente del
     *   comprador que la solicita.
     * - La solicitud debe identificar el pedido y la linea del producto, y
     *   describir el motivo.
     * - Solo es posible devolver pedidos entregados o finalizados.
     * - La cantidad devuelta no puede superar la cantidad comprada (considerando
     *   devoluciones ya solicitadas o aprobadas).
     */
    public ReturnResponse requestReturn(String buyerEmail, ReturnRequest request) {
        var buyer = buyerService.findByUserEntityEmail(buyerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer profile not found for: " + buyerEmail));

        if (request.getOrderId() == null || request.getOrderItemId() == null) {
            throw new BusinessRuleException("La solicitud debe identificar el pedido y la linea del producto");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessRuleException("La solicitud debe describir el motivo de la devolucion");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessRuleException("La cantidad a devolver debe ser mayor que cero");
        }

        OrderEntity order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new BusinessRuleException(
                    "La devolucion debe corresponder a un pedido del comprador que la solicita");
        }
        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.FINALIZED) {
            throw new BusinessRuleException(
                    "Solo es posible devolver pedidos entregados o finalizados (estado actual: " + order.getStatus() + ")");
        }

        OrderItemEntity orderItem = order.getItems() == null ? null : order.getItems().stream()
                .filter(i -> i.getId().equals(request.getOrderItemId()))
                .findFirst()
                .orElse(null);
        if (orderItem == null) {
            throw new BusinessRuleException(
                    "La linea indicada no pertenece al pedido " + request.getOrderId());
        }

        int alreadyRequested = returnRepository.findByOrderItemId(orderItem.getId()).stream()
                .filter(r -> STATUS_REQUESTED.equals(r.getStatus()) || STATUS_APPROVED.equals(r.getStatus()))
                .mapToInt(ReturnEntity::getQuantity)
                .sum();
        int returnable = orderItem.getQuantity() - alreadyRequested;
        if (request.getQuantity() > returnable) {
            throw new BusinessRuleException(
                    "Cantidad no devoluble para la linea solicitada (disponible: " + returnable + ")");
        }

        ReturnEntity returnEntity = new ReturnEntity();
        returnEntity.setReturnNumber(generateReturnNumber());
        returnEntity.setOrder(order);
        returnEntity.setOrderItem(orderItem);
        returnEntity.setReason(request.getReason());
        returnEntity.setQuantity(request.getQuantity());
        returnEntity.setRefundAmount(orderItem.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity())));
        returnEntity.setStatus(STATUS_REQUESTED);

        ReturnEntity saved = returnRepository.save(returnEntity);
        notificationPort.notifyOrderEvent(order.getId(), "Devolucion solicitada: " + saved.getReturnNumber());

        return toResponse(saved, orderItem);
    }

    /**
     * ApproveReturn (Actor: Administrator).
     * Aprueba una solicitud de devolucion despues de verificar las condiciones.
     * La aprobacion no reincorpora unidades al inventario.
     */
    public ReturnResponse approveReturn(Long returnId, String resolutionNotes) {
        ReturnEntity returnEntity = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));

        if (!STATUS_REQUESTED.equals(returnEntity.getStatus())) {
            throw new BusinessRuleException(
                    "Solo una solicitud REQUESTED puede aprobarse (estado actual: " + returnEntity.getStatus() + ")");
        }

        returnEntity.setStatus(STATUS_APPROVED);
        returnEntity.setResolutionNotes(resolutionNotes);
        returnEntity.setProcessedAt(LocalDateTime.now());

        ReturnEntity saved = returnRepository.save(returnEntity);
        notificationPort.notifyOrderEvent(saved.getOrder().getId(),
                "Devolucion aprobada: " + saved.getReturnNumber());

        return toResponse(saved, saved.getOrderItem());
    }

    // ==================== Persistence / queries ====================

    public ReturnResponse findById(Long id) {
        return returnRepository.findById(id)
                .map(r -> toResponse(r, r.getOrderItem()))
                .orElse(null);
    }

    public java.util.List<ReturnEntity> findByOrder(Long orderId) {
        return returnRepository.findByOrderId(orderId);
    }

    public java.util.List<ReturnEntity> findByStatus(String status) {
        return returnRepository.findByStatus(status);
    }

    // ==================== Internal ====================

    private ReturnResponse toResponse(ReturnEntity entity, OrderItemEntity orderItem) {
        return ReturnResponse.builder()
                .id(entity.getId())
                .returnNumber(entity.getReturnNumber())
                .orderId(entity.getOrder().getId())
                .orderNumber(entity.getOrder().getOrderNumber())
                .orderItemId(entity.getOrderItem().getId())
                .productName(orderItem != null && orderItem.getProduct() != null
                        ? orderItem.getProduct().getName() : null)
                .buyerId(entity.getOrder().getBuyer().getId())
                .reason(entity.getReason())
                .quantity(entity.getQuantity())
                .refundAmount(entity.getRefundAmount())
                .status(entity.getStatus())
                .resolutionNotes(entity.getResolutionNotes())
                .createdAt(entity.getCreatedAt())
                .processedAt(entity.getProcessedAt())
                .build();
    }

    private String generateReturnNumber() {
        String candidate;
        do {
            candidate = "RET-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (returnRepository.findByReturnNumber(candidate).isPresent());
        return candidate;
    }
}
