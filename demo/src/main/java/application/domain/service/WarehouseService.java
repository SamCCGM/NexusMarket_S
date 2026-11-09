package application.domain.service;

import application.persistence.mysql.entity.WarehouseEntity;
import application.persistence.mysql.repository.WarehouseRepository;
import application.domain.enums.WarehouseType;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryMongoService inventoryMongoService;

    public WarehouseEntity save(WarehouseEntity warehouse) {
        return warehouseRepository.save(warehouse);
    }

    /**
     * Regla del SDD (AddStock): el ingreso de unidades solo se permite a un
     * registro de inventario de una bodega autorizada. Para un vendedor,
     * la autorizacion es ser el propietario de la bodega de tipo SELLER.
     * Se ejecuta dentro de la transaccion para poder leer la relacion.
     */
    public WarehouseEntity assertSellerAuthorizedWarehouse(Long warehouseId, Long sellerId) {
        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + warehouseId));
        if (warehouse.getType() != WarehouseType.SELLER
                || warehouse.getSeller() == null
                || !warehouse.getSeller().getId().equals(sellerId)) {
            throw new AccessDeniedException(
                    "Un vendedor solo puede operar el inventario de su propia bodega (bodega "
                            + warehouseId + " no autorizada)");
        }
        return warehouse;
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
        WarehouseEntity warehouse = warehouseRepository.findByIdWithSeller(id)
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
            int occupied = inventoryMongoService.findByWarehouseId(id).stream()
                    .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity())
                    .sum();
            if (updates.getCapacity() < occupied) {
                throw new BusinessRuleException(
                        "La capacidad (" + updates.getCapacity()
                                + ") no puede ser menor que las existencias actuales (" + occupied + ")");
            }
            warehouse.setCapacity(updates.getCapacity());
        }
        warehouse.setActive(updates.isActive());

        validateRequiredFields(warehouse);
        WarehouseEntity saved = warehouseRepository.save(warehouse);
        // El controlador mapea seller.companyName fuera de la transaccion.
        return warehouseRepository.findByIdWithSeller(saved.getId()).orElse(saved);
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
        if (warehouse.getType() == WarehouseType.MARKETPLACE && warehouse.getSeller() != null) {
            throw new BusinessRuleException(
                    "A warehouse of type MARKETPLACE cannot be owned by a seller");
        }
        if (warehouse.getCapacity() != null && warehouse.getCapacity() <= 0) {
            throw new BusinessRuleException("Warehouse capacity must be greater than zero");
        }
    }

    public Optional<WarehouseEntity> findById(Long id) {
        return warehouseRepository.findByIdWithSeller(id);
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

    /**
     * DeleteWarehouse: una bodega con registros de inventario no se elimina,
     * para no dejar existencias huerfanas; en ese caso debe desactivarse.
     */
    public void deleteById(Long id) {
        if (!inventoryMongoService.findByWarehouseId(id).isEmpty()) {
            throw new BusinessRuleException(
                    "La bodega tiene registros de inventario asociados: no puede eliminarse, desactivela");
        }
        warehouseRepository.deleteById(id);
    }
}