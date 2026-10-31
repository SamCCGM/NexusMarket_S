package application.controller.warehouse;

import application.domain.dto.warehouse.WarehouseRequest;
import application.domain.dto.warehouse.WarehouseResponse;
import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.entity.WarehouseEntity;
import application.domain.service.SellerService;
import application.domain.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final SellerService sellerService;

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        
        List<WarehouseEntity> warehouses;
        if (type != null) {
            warehouses = warehouseService.findByType(
                    application.domain.enums.WarehouseType.valueOf(type.toUpperCase()));
        } else if (activeOnly) {
            warehouses = warehouseService.findByActiveTrue();
        } else {
            warehouses = warehouseService.findAll();
        }
        
        return ResponseEntity.ok(warehouses.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getWarehouseById(@PathVariable Long id) {
        return warehouseService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-warehouses")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<WarehouseResponse>> getMyWarehouses(Authentication authentication) {
        SellerEntity seller = sellerService.findByUserEntityEmail(authentication.getName()).orElse(null);
        if (seller == null) {
            // Un administrador no tiene perfil de vendedor: no hay bodegas propias.
            if (isAdmin(authentication)) {
                return ResponseEntity.ok(List.of());
            }
            return ResponseEntity.status(403).build();
        }

        List<WarehouseEntity> warehouses = warehouseService.findBySellerId(seller.getId());
        return ResponseEntity.ok(warehouses.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @RequestBody @Valid WarehouseRequest request,
            Authentication authentication) {

        boolean admin = isAdmin(authentication);
        SellerEntity seller = sellerService.findByUserEntityEmail(authentication.getName()).orElse(null);

        // Una bodega de tipo SELLER siempre pertenece a un vendedor; un
        // administrador solo puede registrar bodegas MARKETPLACE.
        if (seller == null && !admin) {
            return ResponseEntity.status(403).build();
        }
        if (seller == null && "SELLER".equalsIgnoreCase(request.getType())) {
            return ResponseEntity.badRequest().build();
        }

        // RegisterWarehouse (SDD): valida ubicacion y tipo (SELLER requiere vendedor)
        WarehouseEntity warehouse = toEntity(request, seller);
        WarehouseEntity saved = warehouseService.registerWarehouse(warehouse);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable Long id,
            @RequestBody @Valid WarehouseRequest request,
            Authentication authentication) {
        
        return warehouseService.findById(id)
                .map(existing -> {
                    boolean admin = isAdmin(authentication);
                    SellerEntity seller = sellerService
                            .findByUserEntityEmail(authentication.getName()).orElse(null);

                    if (!admin && (seller == null || existing.getSeller() == null
                            || !existing.getSeller().getId().equals(seller.getId()))) {
                        return ResponseEntity.status(403).<WarehouseResponse>build();
                    }

                    // UpdateWarehouse (SDD): valida existencia y reglas de registro
                    WarehouseEntity updated = warehouseService.updateWarehouse(id, toEntity(request, existing.getSeller()));
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id, Authentication authentication) {
        return warehouseService.findById(id)
                .map(existing -> {
                    boolean admin = isAdmin(authentication);
                    SellerEntity seller = sellerService
                            .findByUserEntityEmail(authentication.getName()).orElse(null);

                    if (!admin && (seller == null || existing.getSeller() == null
                            || !existing.getSeller().getId().equals(seller.getId()))) {
                        return ResponseEntity.status(403).<Void>build();
                    }

                    warehouseService.deleteById(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));
    }

    private WarehouseResponse toResponse(WarehouseEntity entity) {
        return WarehouseResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .address(entity.getAddress())
                .city(entity.getCity())
                .state(entity.getState())
                .country(entity.getCountry())
                .postalCode(entity.getPostalCode())
                .type(entity.getType() != null ? entity.getType().name() : null)
                .capacity(entity.getCapacity())
                .active(entity.isActive())
                .sellerId(entity.getSeller() != null ? entity.getSeller().getId() : null)
                .sellerName(entity.getSeller() != null ? entity.getSeller().getCompanyName() : null)
                .build();
    }

    private WarehouseEntity toEntity(WarehouseRequest request, SellerEntity seller) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setName(request.getName());
        warehouse.setAddress(request.getAddress());
        warehouse.setCity(request.getCity());
        warehouse.setState(request.getState());
        warehouse.setCountry(request.getCountry());
        warehouse.setPostalCode(request.getPostalCode());
        warehouse.setType(application.domain.enums.WarehouseType.valueOf(request.getType().toUpperCase()));
        warehouse.setCapacity(request.getCapacity());
        warehouse.setActive(request.isActive());
        warehouse.setSeller(seller);
        return warehouse;
    }

}
