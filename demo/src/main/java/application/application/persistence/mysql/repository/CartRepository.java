package application.persistence.mysql.repository;

import application.persistence.mysql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByBuyerId(Long buyerId);

    void deleteByBuyerId(Long buyerId);
}