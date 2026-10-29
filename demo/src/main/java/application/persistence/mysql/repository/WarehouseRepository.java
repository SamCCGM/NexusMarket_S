package application.persistence.mysql.repository;

import application.persistence.mysql.entity.WarehouseEntity;
import application.domain.enums.WarehouseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {

    List<WarehouseEntity> findBySellerId(Long sellerId);

    List<WarehouseEntity> findByType(WarehouseType type);

    List<WarehouseEntity> findByActiveTrue();

    Optional<WarehouseEntity> findByName(String name);
}