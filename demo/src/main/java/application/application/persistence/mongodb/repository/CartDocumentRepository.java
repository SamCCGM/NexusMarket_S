package application.persistence.mongodb.repository;

import application.persistence.mongodb.document.CartDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartDocumentRepository extends MongoRepository<CartDocument, String> {

    Optional<CartDocument> findByBuyerId(Long buyerId);

    void deleteByBuyerId(Long buyerId);
}