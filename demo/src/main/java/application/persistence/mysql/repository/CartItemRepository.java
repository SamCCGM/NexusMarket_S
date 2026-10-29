package application.persistence.mysql.repository;

import application.persistence.mysql.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    List<CartItemEntity> findByCartId(Long cartId);

    Optional<CartItemEntity> findByCartIdAndProductIdAndVariantId(Long cartId, Long productId, Long variantId);

    void deleteByCartId(Long cartId);
}