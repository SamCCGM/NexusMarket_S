package application.service;

import application.domain.enums.BuyerStatus;
import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.repository.BuyerRepository;
import application.persistence.mysql.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public BuyerEntity save(BuyerEntity buyer) {
        return buyerRepository.save(buyer);
    }

    /**
     * RegisterBuyer (Actor: Administrator / System).
     * Registra un comprador y crea su perfil de comprador.
     *
     * Reglas de negocio:
     * - El correo debe ser único en todo el sistema (no solo entre compradores).
     * - La contraseña es obligatoria y se guarda cifrada.
     * - Crea un usuario con rol {@code BUYER} y su información específica,
     *   sin duplicar el flujo de creación de una cuenta genérica.
     * - El comprador nace con estado {@code ACTIVE}.
     */
    public BuyerEntity registerBuyer(BuyerEntity buyer) {
        if (buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            throw new BusinessRuleException("Email is required to register a buyer");
        }
        if (userRepository.existsByEmail(buyer.getEmail())) {
            throw new BusinessRuleException("Email is already registered: " + buyer.getEmail());
        }
        if (buyer.getPassword() == null || buyer.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required to register a buyer");
        }

        buyer.setRole(UserRole.BUYER);
        buyer.setStatus(UserStatus.ACTIVE);
        buyer.setBuyerStatus(BuyerStatus.ACTIVE);
        buyer.setPassword(passwordEncoder.encode(buyer.getPassword()));

        return buyerRepository.save(buyer);
    }

    /**
     * UpdateBuyer (Actor: Buyer / Administrator).
     * Actualiza la información permitida del comprador.
     *
     * Reglas de negocio:
     * - El comprador debe existir.
     * - El correo debe seguir siendo único si cambia.
     * - Solo se actualiza información del perfil (nombre, teléfono, direcciones).
     *   El rol y el estado no se modifican aquí (se gestionan con los servicios
     *   de usuario BlockUser/ActivateUser).
     * - La información de direcciones debe ser válida para el uso que se le dará.
     */
    public BuyerEntity updateBuyer(Long buyerId, BuyerEntity updates) {
        BuyerEntity buyer = buyerRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found: " + buyerId));

        if (updates.getFullName() != null) {
            buyer.setFullName(updates.getFullName());
        }
        if (updates.getPhoneNumber() != null) {
            buyer.setPhoneNumber(updates.getPhoneNumber());
        }
        if (updates.getEmail() != null && !updates.getEmail().equals(buyer.getEmail())) {
            if (userRepository.existsByEmail(updates.getEmail())) {
                throw new BusinessRuleException("Email is already in use: " + updates.getEmail());
            }
            buyer.setEmail(updates.getEmail());
        }

        // Información específica del comprador.
        if (updates.getMainAddress() != null) {
            buyer.setMainAddress(updates.getMainAddress());
        }
        if (updates.getAdditionalAddresses() != null) {
            buyer.setAdditionalAddresses(updates.getAdditionalAddresses());
        }

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
