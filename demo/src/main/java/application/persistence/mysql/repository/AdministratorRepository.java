package application.persistence.mysql.repository;

import application.persistence.mysql.entity.AdministratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministratorRepository extends JpaRepository<AdministratorEntity, Long> {

    // Herencia JOINED: el id del administrador es el mismo id del usuario.
    @Query("SELECT a FROM AdministratorEntity a WHERE a.id = :userId")
    Optional<AdministratorEntity> findByUserEntityId(@Param("userId") Long userId);

    List<AdministratorEntity> findBySuperAdminTrue();
}