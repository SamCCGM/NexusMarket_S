package application.persistence.mysql.repository;

import application.persistence.mysql.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {

    List<InventoryEntity> findByWarehouseId(Long warehouseId);

    List<InventoryEntity> findByProductId(Long productId);

    Optional<InventoryEntity> findByWarehouseIdAndProductIdAndVariantId(Long warehouseId, Long productId, Long variantId);

    List<InventoryEntity> findByWarehouseIdAndProductId(Long warehouseId, Long productId);
}