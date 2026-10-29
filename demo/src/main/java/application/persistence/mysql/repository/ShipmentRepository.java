package application.persistence.mysql.repository;

import application.persistence.mysql.entity.ShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<ShipmentEntity, Long> {

    List<ShipmentEntity> findByOrderId(Long orderId);

    List<ShipmentEntity> findByLogisticsOperatorId(Long logisticsOperatorId);

    List<ShipmentEntity> findByWarehouseId(Long warehouseId);

    Optional<ShipmentEntity> findByTrackingNumber(String trackingNumber);

    List<ShipmentEntity> findByStatus(String status);
}