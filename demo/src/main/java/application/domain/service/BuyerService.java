package application.domain.service;

import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.repository.BuyerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BuyerService {

    @Autowired
    private BuyerRepository buyerRepository;

    public BuyerEntity save(BuyerEntity buyer) {
        return buyerRepository.save(buyer);
    }

    public Optional<BuyerEntity> findById(Long id) {
        return buyerRepository.findById(id);
    }

    public Optional<BuyerEntity> findByUserEntityId(Long userId) {
        return buyerRepository.findByUserEntityId(userId);
    }

    public Optional<BuyerEntity> findByUserEntityEmail(String email) {
        return buyerRepository.findByUserEntityEmail(email);
    }

    public List<BuyerEntity> findAll() {
        return buyerRepository.findAll();
    }

    public List<BuyerEntity> findByBuyerStatus(String status) {
        return buyerRepository.findByBuyerStatus(status);
    }

    public void deleteById(Long id) {
        buyerRepository.deleteById(id);
    }
}