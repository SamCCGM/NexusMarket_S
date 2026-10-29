package application.service;

import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.repository.SellerRepository;
import application.persistence.mysql.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public SellerEntity save(SellerEntity seller) {
        return sellerRepository.save(seller);
    }

    /**
     * RegisterSeller (Actor: Administrator).
     * Incorpora un vendedor al Marketplace.
     *
     * Reglas de negocio:
     * - Los vendedores no pueden registrarse por sí mismos: la incorporación
     *   debe realizarla un administrador (se controla en la capa de API con
     *   la autorización correspondiente).
     * - El correo debe ser único en todo el sistema.
     * - La contraseña es obligatoria y se guarda cifrada.
     * - Crea un usuario con rol {@code SELLER} y su información específica.
     */
    public SellerEntity registerSeller(SellerEntity seller) {
        if (seller.getEmail() == null || seller.getEmail().isBlank()) {
            throw new BusinessRuleException("Email is required to register a seller");
        }
        if (userRepository.existsByEmail(seller.getEmail())) {
            throw new BusinessRuleException("Email is already registered: " + seller.getEmail());
        }
        if (seller.getPassword() == null || seller.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required to register a seller");
        }

        seller.setRole(UserRole.SELLER);
        seller.setStatus(UserStatus.ACTIVE);
        seller.setPassword(passwordEncoder.encode(seller.getPassword()));

        return sellerRepository.save(seller);
    }

    /**
     * UpdateSeller (Actor: Administrator / Seller).
     * Actualiza la información permitida del vendedor.
     *
     * Reglas de negocio:
     * - El vendedor debe existir.
     * - El correo debe seguir siendo único si cambia.
     * - Solo se actualiza información del perfil (nombre, teléfono, empresa,
     *   tax id, descripción). El rol y el estado no se modifican aquí; las
     *   métricas (rating, totalSales) las gestiona el sistema.
     */
    public SellerEntity updateSeller(Long sellerId, SellerEntity updates) {
        SellerEntity seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        if (updates.getFullName() != null) {
            seller.setFullName(updates.getFullName());
        }
        if (updates.getPhoneNumber() != null) {
            seller.setPhoneNumber(updates.getPhoneNumber());
        }
        if (updates.getEmail() != null && !updates.getEmail().equals(seller.getEmail())) {
            if (userRepository.existsByEmail(updates.getEmail())) {
                throw new BusinessRuleException("Email is already in use: " + updates.getEmail());
            }
            seller.setEmail(updates.getEmail());
        }

        // Información específica del vendedor.
        if (updates.getCompanyName() != null) {
            seller.setCompanyName(updates.getCompanyName());
        }
        if (updates.getTaxId() != null) {
            seller.setTaxId(updates.getTaxId());
        }
        if (updates.getDescription() != null) {
            seller.setDescription(updates.getDescription());
        }

        return sellerRepository.save(seller);
    }

    /**
     * BlockSeller (Actor: Administrator).
     * Bloquea la participación de un vendedor en el Marketplace.
     *
     * Reglas de negocio:
     * - El vendedor debe existir y no estar ya bloqueado.
     * - Un vendedor bloqueado no puede publicar productos ni operar.
     * - Los productos y el historial de pedidos se conservan: solo cambia el
     *   estado (no se borra nada).
     */
    public SellerEntity blockSeller(Long sellerId) {
        SellerEntity seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        if (seller.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessRuleException("Seller is already blocked: " + sellerId);
        }

        seller.setStatus(UserStatus.BLOCKED);
        return sellerRepository.save(seller);
    }

    /**
     * ActivateSeller (Actor: Administrator).
     * Reactiva a un vendedor previamente bloqueado.
     *
     * Reglas de negocio:
     * - El vendedor debe existir y estar bloqueado (no se reactiva uno ya activo).
     */
    public SellerEntity activateSeller(Long sellerId) {
        SellerEntity seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found: " + sellerId));

        if (seller.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("Seller is already active: " + sellerId);
        }

        seller.setStatus(UserStatus.ACTIVE);
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
