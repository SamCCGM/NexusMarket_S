package application.persistence.mysql.repository;

import application.persistence.mysql.entity.SellerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<SellerEntity, Long> {

    Optional<SellerEntity> findByUserEntityId(Long userId);

    @Query("SELECT s FROM SellerEntity s JOIN s.userEntity u WHERE u.email = :email")
    Optional<SellerEntity> findByUserEntityEmail(@Param("email") String email);

    List<SellerEntity> findByCompanyNameContainingIgnoreCase(String companyName);
}