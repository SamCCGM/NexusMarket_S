package application.controller.refund;

import application.domain.dto.refund.RefundResponse;
import application.domain.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API de Refund (SDD - Refund Services): ProcessRefund (Administrator).
 * El reembolso solo atiende devoluciones aprobadas y no modifica inventario.
 */
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * ProcessRefund (SDD): registra el reembolso de una devolucion aprobada
     * de un pedido con pago confirmado.
     */
    @PostMapping("/return/{returnId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<RefundResponse> processRefund(@PathVariable Long returnId) {
        return ResponseEntity.ok(refundService.processRefund(returnId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<RefundResponse> getById(@PathVariable Long id) {
        RefundResponse response = refundService.findById(id);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<RefundResponse>> getByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(refundService.findByOrder(orderId).stream()
                .map(r -> RefundResponse.builder()
                        .id(r.getId())
                        .refundNumber(r.getRefundNumber())
                        .returnId(r.getReturnEntity().getId())
                        .returnNumber(r.getReturnEntity().getReturnNumber())
                        .orderId(r.getOrder().getId())
                        .orderNumber(r.getOrder().getOrderNumber())
                        .amount(r.getAmount())
                        .paymentMethod(r.getPaymentMethod())
                        .transactionId(r.getTransactionId())
                        .status(r.getStatus())
                        .createdAt(r.getCreatedAt())
                        .processedAt(r.getProcessedAt())
                        .build())
                .collect(Collectors.toList()));
    }
}
