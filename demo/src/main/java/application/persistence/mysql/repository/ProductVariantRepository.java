package application.persistence.mysql.repository;

import application.persistence.mysql.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {

    List<ProductVariantEntity> findByProductId(Long productId);

    List<ProductVariantEntity> findByProductIdAndActiveTrue(Long productId);

    Optional<ProductVariantEntity> findBySku(String sku);

    boolean existsBySku(String sku);
}