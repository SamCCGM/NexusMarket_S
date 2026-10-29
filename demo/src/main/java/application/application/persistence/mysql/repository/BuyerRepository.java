package application.persistence.mysql.repository;

import application.persistence.mysql.entity.BuyerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuyerRepository extends JpaRepository<BuyerEntity, Long> {

    // Herencia JOINED: el id del comprador es el mismo id del usuario.
    @Query("SELECT b FROM BuyerEntity b WHERE b.id = :userId")
    Optional<BuyerEntity> findByUserEntityId(@Param("userId") Long userId);

    @Query("SELECT b FROM BuyerEntity b WHERE b.email = :email")
    Optional<BuyerEntity> findByUserEntityEmail(@Param("email") String email);

    List<BuyerEntity> findByBuyerStatus(String status);
}