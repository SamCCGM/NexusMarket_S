package application.persistence.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Data
@SuperBuilder
@NoArgsConstructor
public class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", unique = true, length = 100)
    private String trackingNumber;

    /**
     * Identificador del envio en el proveedor externo de despacho
     * (resultado de la llamada al ShipmentPort / Outputs Ports.md).
     */
    @Column(name = "external_shipment_id")
    private Long externalShipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logistics_operator_id")
    private LogisticsOperatorEntity logisticsOperator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private WarehouseEntity warehouse;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "status", length = 50)
    private String status = "PENDING";

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "origin_street")),
            @AttributeOverride(name = "city", column = @Column(name = "origin_city")),
            @AttributeOverride(name = "state", column = @Column(name = "origin_state")),
            @AttributeOverride(name = "country", column = @Column(name = "origin_country")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "origin_postal_code")),
            @AttributeOverride(name = "phoneNumber", column = @Column(name = "origin_phone_number")),
            @AttributeOverride(name = "isDefault", column = @Column(name = "origin_is_default"))
    })
    private AddressEmbeddable originAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "destination_street")),
            @AttributeOverride(name = "city", column = @Column(name = "destination_city")),
            @AttributeOverride(name = "state", column = @Column(name = "destination_state")),
            @AttributeOverride(name = "country", column = @Column(name = "destination_country")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "destination_postal_code")),
            @AttributeOverride(name = "phoneNumber", column = @Column(name = "destination_phone_number")),
            @AttributeOverride(name = "isDefault", column = @Column(name = "destination_is_default"))
    })
    private AddressEmbeddable destinationAddress;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    @Column(name = "shipping_cost", precision = 10, scale = 2)
    private java.math.BigDecimal shippingCost;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
