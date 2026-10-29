package application.persistence.mysql.repository;

import application.persistence.mysql.entity.ProductEntity;
import application.domain.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findBySellerId(Long sellerId);

    List<ProductEntity> findByStatus(ProductStatus status);

    Page<ProductEntity> findByStatus(ProductStatus status, Pageable pageable);

    List<ProductEntity> findByCategory(String category);

    List<ProductEntity> findBySellerIdAndStatus(Long sellerId, ProductStatus status);

    @Query("SELECT p FROM ProductEntity p WHERE p.name ILIKE %:keyword% OR p.description ILIKE %:keyword%")
    List<ProductEntity> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT p FROM ProductEntity p WHERE p.sku = :sku")
    Optional<ProductEntity> findBySku(@Param("sku") String sku);

    boolean existsBySku(String sku);

    // Carga el producto junto con su vendedor en una sola consulta (evita
    // LazyInitializationException con open-in-view=false).
    @Query("SELECT p FROM ProductEntity p JOIN FETCH p.seller s WHERE p.id = :id")
    Optional<ProductEntity> findByIdWithSeller(@Param("id") Long id);
}