package application.domain.service;

import application.domain.dto.refund.RefundResponse;
import application.domain.ports.NotificationPort;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.RefundEntity;
import application.persistence.mysql.entity.ReturnEntity;
import application.persistence.mysql.repository.RefundRepository;
import application.persistence.mysql.repository.ReturnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicios de dominio de Refund (SDD - Refund Services).
 *
 * Regla clave: el reembolso y la reincorporacion de unidades al inventario son
 * operaciones distintas; el inventario solo se actualiza cuando las unidades
 * hayan sido recibidas y declaradas aptas (operacion ReturnStock aparte).
 * En esta primera entrega no se integra una pasarela de pagos externa: el
 * resultado del proceso se registra localmente.
 */
@Service
@Transactional
public class RefundService {

    private static final String STATUS_COMPLETED = "COMPLETED";

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private NotificationPort notificationPort;

    /**
     * ProcessRefund (Actor: Administrator / System).
     * Registra el reembolso correspondiente a una devolucion aprobada.
     *
     * Reglas de negocio:
     * - El reembolso debe estar asociado a una devolucion existente y APROBADA.
     * - El reembolso corresponde a un pedido con pago confirmado.
     * - No se reembolsa dos veces la misma devolucion.
     * - No modifica el inventario (eso corresponde a ReturnStock).
     */
    public RefundResponse processRefund(Long returnId) {
        ReturnEntity returnEntity = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));

        if (!"APPROVED".equals(returnEntity.getStatus())) {
            throw new BusinessRuleException(
                    "Solo las devoluciones APROBADAS pueden reembolsarse (estado actual: " + returnEntity.getStatus() + ")");
        }

        boolean alreadyProcessed = refundRepository.findByReturnId(returnId).stream()
                .anyMatch(r -> STATUS_COMPLETED.equals(r.getStatus()));
        if (alreadyProcessed) {
            throw new BusinessRuleException("La devolucion " + returnId + " ya tiene un reembolso registrado");
        }

        if (returnEntity.getOrder().getPaidAt() == null) {
            throw new BusinessRuleException(
                    "El reembolso corresponde a un pedido con pago confirmado; el pedido "
                            + returnEntity.getOrder().getId() + " no registra pago confirmado");
        }

        RefundEntity refund = new RefundEntity();
        refund.setRefundNumber(generateRefundNumber());
        refund.setReturnEntity(returnEntity);
        refund.setOrder(returnEntity.getOrder());
        refund.setAmount(returnEntity.getRefundAmount());
        refund.setPaymentMethod("ORIGINAL_METHOD");
        refund.setTransactionId("RFND-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        refund.setStatus(STATUS_COMPLETED);
        refund.setProcessedAt(LocalDateTime.now());

        RefundEntity saved = refundRepository.save(refund);
        notificationPort.notifyOrderEvent(saved.getOrder().getId(),
                "Reembolso registrado: " + saved.getRefundNumber());

        return toResponse(saved);
    }

    // ==================== Persistence / queries ====================

    public RefundResponse findById(Long id) {
        return refundRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    public java.util.List<RefundEntity> findByOrder(Long orderId) {
        return refundRepository.findByOrderId(orderId);
    }

    // ==================== Internal ====================

    private RefundResponse toResponse(RefundEntity entity) {
        return RefundResponse.builder()
                .id(entity.getId())
                .refundNumber(entity.getRefundNumber())
                .returnId(entity.getReturnEntity().getId())
                .returnNumber(entity.getReturnEntity().getReturnNumber())
                .orderId(entity.getOrder().getId())
                .orderNumber(entity.getOrder().getOrderNumber())
                .amount(entity.getAmount())
                .paymentMethod(entity.getPaymentMethod())
                .transactionId(entity.getTransactionId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .processedAt(entity.getProcessedAt())
                .build();
    }

    private String generateRefundNumber() {
        String candidate;
        do {
            candidate = "RFD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (refundRepository.findByRefundNumber(candidate).isPresent());
        return candidate;
    }
}
