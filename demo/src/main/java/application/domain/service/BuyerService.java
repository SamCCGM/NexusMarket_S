package application.domain.service;

import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.repository.BuyerRepository;
import application.domain.enums.BuyerStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
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

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * RegisterBuyer: registra un comprador. El correo y la contrasena son
     * obligatorios y el comprador inicia como comprador activo.
     */
    public BuyerEntity registerBuyer(BuyerEntity buyer) {
        if (buyer.getFullName() == null || buyer.getFullName().isBlank()) {
            throw new BusinessRuleException("El nombre completo es obligatorio para registrar un comprador");
        }
        if (buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            throw new BusinessRuleException("El correo es obligatorio para registrar un comprador");
        }
        if (buyer.getPassword() == null || buyer.getPassword().isBlank()) {
            throw new BusinessRuleException("La contrasena es obligatoria para registrar un comprador");
        }
        // La contrasena se guarda siempre cifrada (login via SecurityConfig).
        buyer.setPassword(passwordEncoder.encode(buyer.getPassword()));
        buyer.setBuyerStatus(BuyerStatus.ACTIVE);
        if (buyer.getStatus() == null) {
            buyer.setStatus(application.domain.enums.UserStatus.ACTIVE);
        }
        if (buyer.getRole() == null) {
            buyer.setRole(application.domain.enums.UserRole.BUYER);
        }
        return buyerRepository.save(buyer);
    }

    /**
     * UpdateBuyer: actualiza los datos del comprador.
     */
    public BuyerEntity updateBuyer(Long buyerId, BuyerEntity updates) {
        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprador no encontrado con id: " + buyerId));
        if (updates.getFullName() != null && !updates.getFullName().isBlank()) {
            buyer.setFullName(updates.getFullName());
        }
        if (updates.getPhoneNumber() != null) {
            buyer.setPhoneNumber(updates.getPhoneNumber());
        }
        if (updates.getMainAddress() != null) {
            buyer.setMainAddress(updates.getMainAddress());
        }
        return buyerRepository.save(buyer);
    }

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

    public List<BuyerEntity> findByBuyerStatus(BuyerStatus status) {
        return buyerRepository.findByBuyerStatus(status);
    }

    public void deleteById(Long id) {
        buyerRepository.deleteById(id);
    }
}