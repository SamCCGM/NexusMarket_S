package application.persistence.mongodb.repository;

import application.persistence.mongodb.document.InventoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryDocumentRepository extends MongoRepository<InventoryDocument, String> {

    List<InventoryDocument> findByWarehouseId(Long warehouseId);

    List<InventoryDocument> findByProductId(Long productId);

    Optional<InventoryDocument> findByWarehouseIdAndProductIdAndVariantId(Long warehouseId, Long productId, String variantId);

    List<InventoryDocument> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    List<InventoryDocument> findByQuantityLessThanEqual(Integer threshold);
}