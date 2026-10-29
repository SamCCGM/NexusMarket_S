package application.persistence.mysql.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "logistics_operators")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LogisticsOperatorEntity extends UserEntity {

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Column(name = "vehicle_info", columnDefinition = "TEXT")
    private String vehicleInfo;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "is_available")
    private boolean available = true;

    @OneToMany(mappedBy = "logisticsOperator", cascade = CascadeType.ALL)
    private List<ShipmentEntity> shipments;
}