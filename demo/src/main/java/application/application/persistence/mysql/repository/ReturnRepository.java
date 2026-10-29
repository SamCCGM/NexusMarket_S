package application.persistence.mysql.repository;

import application.persistence.mysql.entity.ReturnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<ReturnEntity, Long> {

    List<ReturnEntity> findByOrderId(Long orderId);

    List<ReturnEntity> findByOrderItemId(Long orderItemId);

    List<ReturnEntity> findByStatus(String status);

    Optional<ReturnEntity> findByReturnNumber(String returnNumber);
}