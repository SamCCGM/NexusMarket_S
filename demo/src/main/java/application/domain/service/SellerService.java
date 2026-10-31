package application.domain.service;

import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.repository.SellerRepository;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
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

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public SellerEntity save(SellerEntity seller) {
        return sellerRepository.save(seller);
    }

    public Optional<SellerEntity> findById(Long id) {
        return sellerRepository.findById(id);
    }

    public Optional<SellerEntity> findByUserEntityId(Long userId) {
        return sellerRepository.findByUserEntityId(userId);
    }

    public Optional<SellerEntity> findByUserEntityEmail(String email) {
        return sellerRepository.findByUserEntityEmail(email);
    }

    /**
     * RegisterSeller: da de alta a un vendedor con su compañia.
     * Regla: los datos obligatorios (nombre de compania y correo) deben estar presentes.
     */
    public SellerEntity registerSeller(SellerEntity seller) {
        if (seller.getCompanyName() == null || seller.getCompanyName().isBlank()) {
            throw new BusinessRuleException("El nombre de la compania es obligatorio para registrar un vendedor");
        }
        if (seller.getEmail() == null || seller.getEmail().isBlank()) {
            throw new BusinessRuleException("El correo es obligatorio para registrar un vendedor");
        }
        if (seller.getPassword() == null || seller.getPassword().isBlank()) {
            throw new BusinessRuleException("La contrasena es obligatoria para registrar un vendedor");
        }
        if (seller.getStatus() == null) {
            seller.setStatus(UserStatus.ACTIVE);
        }
        if (seller.getRole() == null) {
            seller.setRole(application.domain.enums.UserRole.SELLER);
        }
        // La contrasena se guarda siempre cifrada (login via SecurityConfig).
        seller.setPassword(passwordEncoder.encode(seller.getPassword()));
        return sellerRepository.save(seller);
    }

    /**
     * UpdateSeller: actualiza los datos del vendedor. No permite desactivar
     * ni eliminar un vendedor a traves de esta operacion.
     */
    public SellerEntity updateSeller(SellerEntity seller) {
        SellerEntity existing = sellerRepository.findById(seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado con id: " + seller.getId()));
        if (existing.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessRuleException("Un vendedor bloqueado no puede actualizar su informacion");
        }
        existing.setCompanyName(seller.getCompanyName());
        existing.setTaxId(seller.getTaxId());
        existing.setDescription(seller.getDescription());
        return sellerRepository.save(existing);
    }

    /**
     * BlockSeller: impide que el vendedor siga operando en la plataforma.
     */
    public SellerEntity blockSeller(Long sellerId) {
        SellerEntity seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado con id: " + sellerId));
        if (seller.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessRuleException("El vendedor ya se encuentra bloqueado");
        }
        seller.setStatus(UserStatus.BLOCKED);
        return sellerRepository.save(seller);
    }

    /**
     * ActivateSeller: reactiva un vendedor previamente bloqueado.
     */
    public SellerEntity activateSeller(Long sellerId) {
        SellerEntity seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendedor no encontrado con id: " + sellerId));
        if (seller.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("El vendedor ya se encuentra activo");
        }
        seller.setStatus(UserStatus.ACTIVE);
        return sellerRepository.save(seller);
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