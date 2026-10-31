package application.controller.buyer;

import application.domain.dto.buyer.BuyerRegisterRequest;
import application.domain.dto.buyer.BuyerResponse;
import application.domain.dto.buyer.BuyerUpdateRequest;
import application.persistence.mysql.entity.AddressEmbeddable;
import application.persistence.mysql.entity.BuyerEntity;
import application.domain.service.BuyerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/buyers")
@RequiredArgsConstructor
public class BuyerController {

    private final BuyerService buyerService;

    /**
     * RegisterBuyer (SDD): alta de un comprador. El registro publico ocurre en
     * POST /api/auth/register (solo compradores); este endpoint permite la
     * incorporacion directa de compradores por un administrador.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BuyerResponse> registerBuyer(@RequestBody @Valid BuyerRegisterRequest request) {
        if (buyerService.findByUserEntityEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        BuyerEntity buyer = BuyerEntity.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(request.getPassword())
                .phoneNumber(request.getPhoneNumber())
                .build();
        return ResponseEntity.ok(toResponse(buyerService.registerBuyer(buyer)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<BuyerResponse>> getAllBuyers() {
        return ResponseEntity.ok(buyerService.findAll().stream()
                .map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR') or hasRole('SELLER')")
    public ResponseEntity<BuyerResponse> getBuyerById(@PathVariable Long id) {
        return buyerService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * UpdateBuyer (SDD): el comprador actualiza su propia informacion.
     * Solo el propietario o un administrador pueden actualizarla.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BUYER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<BuyerResponse> updateBuyer(
            @PathVariable Long id,
            @RequestBody BuyerUpdateRequest request,
            Authentication authentication) {

        return buyerService.findById(id)
                .map(existing -> {
                    boolean isOwner = authentication.getName().equals(existing.getEmail());
                    boolean isAdmin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));
                    if (!isOwner && !isAdmin) {
                        return ResponseEntity.status(403).<BuyerResponse>build();
                    }
                    return ResponseEntity.ok(toResponse(buyerService.updateBuyer(id, toUpdates(request))));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private BuyerEntity toUpdates(BuyerUpdateRequest request) {
        BuyerEntity updates = new BuyerEntity();
        updates.setFullName(request.getFullName());
        updates.setPhoneNumber(request.getPhoneNumber());
        if (request.getStreet() != null || request.getCity() != null || request.getCountry() != null) {
            updates.setMainAddress(AddressEmbeddable.builder()
                    .street(request.getStreet())
                    .city(request.getCity())
                    .state(request.getState())
                    .country(request.getCountry())
                    .postalCode(request.getPostalCode())
                    .phoneNumber(request.getMainPhoneNumber())
                    .build());
        }
        return updates;
    }

    private BuyerResponse toResponse(BuyerEntity entity) {
        return BuyerResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .buyerStatus(entity.getBuyerStatus() != null ? entity.getBuyerStatus().name() : null)
                .street(entity.getMainAddress() != null ? entity.getMainAddress().getStreet() : null)
                .city(entity.getMainAddress() != null ? entity.getMainAddress().getCity() : null)
                .state(entity.getMainAddress() != null ? entity.getMainAddress().getState() : null)
                .country(entity.getMainAddress() != null ? entity.getMainAddress().getCountry() : null)
                .postalCode(entity.getMainAddress() != null ? entity.getMainAddress().getPostalCode() : null)
                .build();
    }
}
