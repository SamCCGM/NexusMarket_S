package application.persistence.mysql.repository;

import application.persistence.mysql.entity.WarehouseEntity;
import application.domain.enums.WarehouseType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {

    @EntityGraph(attributePaths = "seller")
    List<WarehouseEntity> findBySellerId(Long sellerId);

    @EntityGraph(attributePaths = "seller")
    List<WarehouseEntity> findByType(WarehouseType type);

    @EntityGraph(attributePaths = "seller")
    List<WarehouseEntity> findByActiveTrue();

    @EntityGraph(attributePaths = "seller")
    Optional<WarehouseEntity> findByName(String name);

    @EntityGraph(attributePaths = "seller")
    @Query("SELECT w FROM WarehouseEntity w WHERE w.id = :id")
    Optional<WarehouseEntity> findByIdWithSeller(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "seller")
    List<WarehouseEntity> findAll();
}
