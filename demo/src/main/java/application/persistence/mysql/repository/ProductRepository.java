package application.persistence.mysql.repository;

import application.persistence.mysql.entity.ProductEntity;
import application.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Todas las consultas cargan el vendedor junto con el producto (@EntityGraph /
 * JOIN FETCH) para evitar LazyInitializationException con open-in-view=false.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    @EntityGraph(attributePaths = {"seller", "variants"})
    List<ProductEntity> findBySellerId(Long sellerId);

    @EntityGraph(attributePaths = {"seller", "variants"})
    List<ProductEntity> findByStatus(ProductStatus status);

    @EntityGraph(attributePaths = {"seller", "variants"})
    Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "variants"})
    List<ProductEntity> findByCategory(String category);

    @EntityGraph(attributePaths = {"seller", "variants"})
    List<ProductEntity> findBySellerIdAndStatus(Long sellerId, ProductStatus status);

    @EntityGraph(attributePaths = {"seller", "variants"})
    @Query("SELECT p FROM ProductEntity p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<ProductEntity> searchByKeyword(@Param("keyword") String keyword);

    @EntityGraph(attributePaths = {"seller", "variants"})
    @Query("SELECT p FROM ProductEntity p WHERE p.sku = :sku")
    Optional<ProductEntity> findBySku(@Param("sku") String sku);

    boolean existsBySku(String sku);

    // Carga el producto junto con su vendedor y variantes (evita
    // LazyInitializationException con open-in-view=false).
    @EntityGraph(attributePaths = {"seller", "variants"})
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
    Optional<ProductEntity> findByIdWithSeller(@Param("id") Long id);
}
