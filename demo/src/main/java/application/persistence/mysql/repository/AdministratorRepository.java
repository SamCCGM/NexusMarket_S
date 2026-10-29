package application.persistence.mysql.repository;

import application.persistence.mysql.entity.AdministratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministratorRepository extends JpaRepository<AdministratorEntity, Long> {

    Optional<AdministratorEntity> findByUserEntityId(Long userId);

    List<AdministratorEntity> findBySuperAdminTrue();
}