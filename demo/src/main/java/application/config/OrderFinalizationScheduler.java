package application.config;

import application.domain.enums.OrderStatus;
import application.domain.service.OrderService;
import application.persistence.mysql.entity.OrderEntity;
import application.persistence.mysql.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FinalizeOrder (Actor: System).
 * Actuacion automatica de cierre de pedidos: FinalizeOrder es una accion del
 * sistema que se ejecuta cuando se cumple la condicion de finalizacion
 * definida por el sistema, sin depender de que un administrador la dispare:
 *
 * - Pedidos fisicos: DELIVERED hace mas de N horas (ventana configurable) se
 *   dan por concluidos; en esa ventana el comprador podia solicitar
 *   devoluciones (RequestReturn admite DELIVERED y FINALIZED).
 * - Pedidos virtuales: quedaron DELIVERED al confirmar el pago; la misma
 *   ventana de conclusión aplica despues de la entrega digital.
 *
 * Solo pasa a FINALIZED un pedido DELIVERED (la maquina de estados confirma
 * que no hay saltos). Se notifica al comprador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderFinalizationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** Ventana (en horas) entre la entrega y el cierre automatico. */
    @Value("${nexusmarket.order-finalization-delay-hours:24}")
    private long finalizationDelayHours;

    @Value("${nexusmarket.order-finalization-enabled:true}")
    private boolean enabled;

    @Value("${nexusmarket.order-expiration-hours:24}")
    private long expirationHours;

    /**
     * OrderExpiration (Actor: System).
     * Cancela los pedidos PENDING_PAYMENT que superaron la ventana de pago y
     * libera la reserva de inventario asociada (evita stock bloqueado por
     * carritos/pedidos abandonados).
     */
    @Scheduled(fixedDelayString = "${nexusmarket.order-expiration-scheduler-ms:3600000}",
            initialDelayString = "${nexusmarket.order-expiration-initial-ms:120000}")
    public void autoExpireUnpaidOrders() {
        if (!enabled) {
            return;
        }
        int expired = orderService.expireUnpaidOrders((int) expirationHours);
        if (expired > 0) {
            log.info("OrderExpiration automatico: {} pedidos cancelados por falta de pago", expired);
        }
    }

    /** Corre cada hora (configurable en application.properties). */
    @Scheduled(fixedDelayString = "${nexusmarket.order-finalization-scheduler-ms:3600000}",
            initialDelayString = "${nexusmarket.order-finalization-initial-ms:60000}")
    public void autoFinalizeDeliveredOrders() {
        if (!enabled) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(finalizationDelayHours);
        List<OrderEntity> ready = orderRepository
                .findByStatusAndDeliveredAtBefore(OrderStatus.DELIVERED, cutoff);

        for (OrderEntity order : ready) {
            try {
                OrderEntity finalized = orderService.finalizeOrder(order.getId());
                log.info("FinalizeOrder automatico: pedido {} pasado a FINALIZED (entregado el {})",
                        finalized.getOrderNumber(), order.getDeliveredAt());
            } catch (Exception e) {
                // Un pedido individual con problema no detiene el lote.
                log.warn("FinalizeOrder automatico fallo para el pedido {}: {}",
                        order.getId(), e.getMessage());
            }
        }
    }
}
