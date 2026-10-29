package application.application.service;

import application.application.persistence.mysql.entity.SellerEntity;
import application.application.persistence.mysql.repository.SellerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SellerService {

    @Autowired
    private SellerRepository sellerRepository;

    public SellerEntity save(SellerEntity seller) {
        return sellerRepository.save(seller);
    }

    public Optional<SellerEntity> findById(Long id) {
        return sellerRepository.findById(id);
    }

    public Optional<SellerEntity> findByUserEntityId(Long userId) {
        return sellerRepository.findByUserEntityId(userId);
    }

    public List<SellerEntity> findAll() {
        return sellerRepository.findAll();
    }

    public List<SellerEntity> findByCompanyNameContainingIgnoreCase(String companyName) {
        return sellerRepository.findByCompanyNameContainingIgnoreCase(companyName);
    }

    public void deleteById(Long id) {
        sellerRepository.deleteById(id);
    }
}