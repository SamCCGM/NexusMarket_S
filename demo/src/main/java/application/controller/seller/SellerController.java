package application.controller.seller;

import application.domain.dto.seller.SellerRegisterRequest;
import application.domain.dto.seller.SellerResponse;
import application.domain.dto.seller.SellerUpdateRequest;
import application.persistence.mysql.entity.SellerEntity;
import application.domain.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<SellerResponse>> getAllSellers(
            @RequestParam(required = false) String companyName) {

        List<SellerEntity> sellers = companyName != null
                ? sellerService.findByCompanyNameContainingIgnoreCase(companyName)
                : sellerService.findAll();
        return ResponseEntity.ok(sellers.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SellerResponse> getSellerById(@PathVariable Long id) {
        return sellerService.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<SellerResponse> getMySellerProfile(java.security.Principal principal) {
        return sellerService.findByUserEntityEmail(principal.getName())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * UpdateSeller (SDD): actualiza los datos del vendedor. Un vendedor
     * bloqueado no puede actualizar su informacion.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMINISTRATOR')")
    public ResponseEntity<SellerResponse> updateSeller(
            @PathVariable Long id,
            @RequestBody @Valid SellerUpdateRequest request,
            java.security.Principal principal) {

        return sellerService.findById(id)
                .map(existing -> {
                    boolean isOwner = principal.getName().equals(existing.getEmail());
                    boolean isAdmin = false;
                    org.springframework.security.core.Authentication auth =
                            (org.springframework.security.core.Authentication) principal;
                    if (auth != null && auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"))) {
                        isAdmin = true;
                    }
                    if (!isOwner && !isAdmin) {
                        return ResponseEntity.status(403).<SellerResponse>build();
                    }
                    applyUpdates(existing, request);
                    return ResponseEntity.ok(toResponse(sellerService.updateSeller(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * BlockSeller (SDD): impide que el vendedor siga operando en la plataforma.
     */
    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SellerResponse> blockSeller(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(sellerService.blockSeller(id)));
    }

    /**
     * ActivateSeller (SDD): reactiva un vendedor previamente bloqueado.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SellerResponse> activateSeller(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(sellerService.activateSeller(id)));
    }

    /**
     * RegisterSeller (SDD): incorpora un vendedor. Solo un administrador puede
     * hacerlo (los vendedores no se registran por si mismos).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<SellerResponse> registerSeller(@RequestBody @jakarta.validation.Valid SellerRegisterRequest request) {
        if (sellerService.findByUserEntityEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        SellerEntity seller = SellerEntity.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(request.getPassword())
                .companyName(request.getCompanyName())
                .taxId(request.getTaxId())
                .description(request.getDescription())
                .build();
        return ResponseEntity.ok(toResponse(sellerService.registerSeller(seller)));
    }

    private void applyUpdates(SellerEntity seller, SellerUpdateRequest request) {
        if (request.getCompanyName() != null) {
            seller.setCompanyName(request.getCompanyName());
        }
        if (request.getTaxId() != null) {
            seller.setTaxId(request.getTaxId());
        }
        if (request.getDescription() != null) {
            seller.setDescription(request.getDescription());
        }
    }

    private SellerResponse toResponse(SellerEntity entity) {
        return SellerResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .companyName(entity.getCompanyName())
                .taxId(entity.getTaxId())
                .description(entity.getDescription())
                .rating(entity.getRating())
                .totalSales(entity.getTotalSales())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .build();
    }
}
