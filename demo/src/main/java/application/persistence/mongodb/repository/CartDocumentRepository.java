package application.persistence.mongodb.repository;

import application.persistence.mongodb.document.CartDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartDocumentRepository extends MongoRepository<CartDocument, String> {

    Optional<CartDocument> findByBuyerId(Long buyerId);

    @Query("{ 'items.productId': ?0 }")
    List<CartDocument> findByItemsProductId(Long productId);

    void deleteByBuyerId(Long buyerId);
}