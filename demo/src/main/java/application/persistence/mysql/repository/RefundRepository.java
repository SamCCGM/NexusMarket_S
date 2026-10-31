package application.persistence.mysql.repository;

import application.persistence.mysql.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    List<RefundEntity> findByOrderId(Long orderId);

    @Query("SELECT r FROM RefundEntity r WHERE r.returnEntity.id = :returnId")
    List<RefundEntity> findByReturnId(@Param("returnId") Long returnId);

    List<RefundEntity> findByStatus(String status);

    Optional<RefundEntity> findByRefundNumber(String refundNumber);
}