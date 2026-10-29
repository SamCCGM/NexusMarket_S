package application.persistence.mysql.repository;

import application.persistence.mysql.entity.LogisticsOperatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LogisticsOperatorRepository extends JpaRepository<LogisticsOperatorEntity, Long> {

    // Herencia JOINED: el id del operador es el mismo id del usuario.
    @Query("SELECT l FROM LogisticsOperatorEntity l WHERE l.id = :userId")
    Optional<LogisticsOperatorEntity> findByUserEntityId(@Param("userId") Long userId);

    List<LogisticsOperatorEntity> findByAvailableTrue();

    List<LogisticsOperatorEntity> findByCompanyNameContainingIgnoreCase(String companyName);
}