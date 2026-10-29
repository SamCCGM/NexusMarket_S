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

    Optional<BuyerEntity> findByUserEntityId(Long userId);

    @Query("SELECT b FROM BuyerEntity b JOIN b.userEntity u WHERE u.email = :email")
    Optional<BuyerEntity> findByUserEntityEmail(@Param("email") String email);

    List<BuyerEntity> findByBuyerStatus(String status);
}