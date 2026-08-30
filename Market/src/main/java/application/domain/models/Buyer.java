package application.domain.models;

import application.domain.enums.BuyerStatus;
import application.domain.enums.UserRole;
import application.domain.valueobjects.Address;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Buyer extends User {

    private Address mainAddress;
    private List<Address> additionalAddresses;
    private BuyerStatus buyerStatus;
}