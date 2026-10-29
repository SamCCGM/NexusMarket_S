package application.persistence.mysql.entity;

import application.domain.enums.BuyerStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "buyers")
@PrimaryKeyJoinColumn(name = "id")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BuyerEntity extends UserEntity {

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "main_street")),
        @AttributeOverride(name = "city", column = @Column(name = "main_city")),
        @AttributeOverride(name = "state", column = @Column(name = "main_state")),
        @AttributeOverride(name = "country", column = @Column(name = "main_country")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "main_postal_code")),
        @AttributeOverride(name = "phoneNumber", column = @Column(name = "main_phone_number")),
        @AttributeOverride(name = "isDefault", column = @Column(name = "main_is_default"))
    })
    private AddressEmbeddable mainAddress;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AddressEntity> additionalAddresses;

    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_status")
    private BuyerStatus buyerStatus;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<OrderEntity> orders;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL)
    private List<CartEntity> carts;
}