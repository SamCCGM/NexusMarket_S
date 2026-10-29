package application.service;

import application.persistence.mysql.entity.WarehouseEntity;
import application.persistence.mysql.repository.WarehouseRepository;
import application.domain.enums.WarehouseType;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    public WarehouseEntity save(WarehouseEntity warehouse) {
        return warehouseRepository.save(warehouse);
    }

    /**
     * RegisterWarehouse (Actor: Administrator).
     * Registra una bodega en el sistema.
     *
     * Reglas de negocio:
     * - La bodega tiene un identificador único (generado por el sistema) y una
     *   ubicación válida (nombre y dirección obligatorios).
     * - El tipo debe ser válido según {@link WarehouseType} (MARKETPLACE o SELLER).
     * - Una bodega de tipo SELLER debe estar asociada a un vendedor.
     */
    public WarehouseEntity registerWarehouse(WarehouseEntity warehouse) {
        validateRequiredFields(warehouse);
        return warehouseRepository.save(warehouse);
    }

    /**
     * UpdateWarehouse (Actor: Administrator).
     * Actualiza la información permitida de una bodega existente.
     *
     * Reglas de negocio:
     * - La bodega debe existir.
     * - Se conservan las reglas de validación de registro (ubicación y tipo).
     * - El inventario se administra mediante los registros Inventory; este
     *   servicio no lo modifica.
     */
    public WarehouseEntity updateWarehouse(Long id, WarehouseEntity updates) {
        WarehouseEntity warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + id));

        if (updates.getName() != null) {
            warehouse.setName(updates.getName());
        }
        if (updates.getAddress() != null) {
            warehouse.setAddress(updates.getAddress());
        }
        if (updates.getCity() != null) {
            warehouse.setCity(updates.getCity());
        }
        if (updates.getState() != null) {
            warehouse.setState(updates.getState());
        }
        if (updates.getCountry() != null) {
            warehouse.setCountry(updates.getCountry());
        }
        if (updates.getPostalCode() != null) {
            warehouse.setPostalCode(updates.getPostalCode());
        }
        if (updates.getType() != null) {
            warehouse.setType(updates.getType());
        }
        if (updates.getCapacity() != null) {
            warehouse.setCapacity(updates.getCapacity());
        }

        validateRequiredFields(warehouse);
        return warehouseRepository.save(warehouse);
    }

    private void validateRequiredFields(WarehouseEntity warehouse) {
        if (warehouse.getName() == null || warehouse.getName().isBlank()) {
            throw new BusinessRuleException("Warehouse name is required");
        }
        if (warehouse.getAddress() == null || warehouse.getAddress().isBlank()) {
            throw new BusinessRuleException("Warehouse location (address) is required");
        }
        if (warehouse.getType() == null) {
            throw new BusinessRuleException("Warehouse type is required (MARKETPLACE or SELLER)");
        }
        if (warehouse.getType() == WarehouseType.SELLER && warehouse.getSeller() == null) {
            throw new BusinessRuleException("A warehouse of type SELLER must be associated with a seller");
        }
    }

    public Optional<WarehouseEntity> findById(Long id) {
        return warehouseRepository.findById(id);
    }

    public List<WarehouseEntity> findBySellerId(Long sellerId) {
        return warehouseRepository.findBySellerId(sellerId);
    }

    public List<WarehouseEntity> findByType(WarehouseType type) {
        return warehouseRepository.findByType(type);
    }

    public List<WarehouseEntity> findByActiveTrue() {
        return warehouseRepository.findByActiveTrue();
    }

    public Optional<WarehouseEntity> findByName(String name) {
        return warehouseRepository.findByName(name);
    }

    public List<WarehouseEntity> findAll() {
        return warehouseRepository.findAll();
    }

    public void deleteById(Long id) {
        warehouseRepository.deleteById(id);
    }
}