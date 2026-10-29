package application.config;

import application.domain.enums.UserRole;
import application.domain.enums.UserStatus;
import application.persistence.mysql.entity.AdministratorEntity;
import application.persistence.mysql.entity.BuyerEntity;
import application.persistence.mysql.entity.SellerEntity;
import application.persistence.mysql.entity.UserEntity;
import application.persistence.mysql.repository.AdministratorRepository;
import application.persistence.mysql.repository.BuyerRepository;
import application.persistence.mysql.repository.SellerRepository;
import application.persistence.mysql.repository.UserRepository;
import application.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("dev")
    public CommandLineRunner initData(
            UserRepository userRepository,
            UserService userService,
            BuyerRepository buyerRepository,
            SellerRepository sellerRepository,
            AdministratorRepository administratorRepository) {
        
        return args -> {
            // Create admin user
            if (userRepository.findByEmail("admin@nexusmarket.com").isEmpty()) {
                AdministratorEntity admin = AdministratorEntity.builder()
                        .fullName("System Administrator")
                        .email("admin@nexusmarket.com")
                        .password(passwordEncoder.encode("admin123"))
                        .role(UserRole.ADMINISTRATOR)
                        .status(UserStatus.ACTIVE)
                        .phoneNumber("+1234567890")
                        .department("IT")
                        .superAdmin(true)
                        .build();

                administratorRepository.save(admin);
                System.out.println("Created admin user: admin@nexusmarket.com / admin123");
            }

            // Create test buyer
            if (userRepository.findByEmail("buyer@nexusmarket.com").isEmpty()) {
                BuyerEntity buyer = BuyerEntity.builder()
                        .fullName("Test Buyer")
                        .email("buyer@nexusmarket.com")
                        .password(passwordEncoder.encode("buyer123"))
                        .role(UserRole.BUYER)
                        .status(UserStatus.ACTIVE)
                        .phoneNumber("+1234567891")
                        .buyerStatus(application.domain.enums.BuyerStatus.ACTIVE)
                        .build();

                buyerRepository.save(buyer);
                System.out.println("Created buyer user: buyer@nexusmarket.com / buyer123");
            }

            // Create test seller
            if (userRepository.findByEmail("seller@nexusmarket.com").isEmpty()) {
                SellerEntity seller = SellerEntity.builder()
                        .fullName("Test Seller")
                        .email("seller@nexusmarket.com")
                        .password(passwordEncoder.encode("seller123"))
                        .role(UserRole.SELLER)
                        .status(UserStatus.ACTIVE)
                        .phoneNumber("+1234567892")
                        .companyName("Test Store")
                        .taxId("123456789")
                        .description("Test seller account")
                        .rating(4.5)
                        .totalSales(100L)
                        .build();

                sellerRepository.save(seller);
                System.out.println("Created seller user: seller@nexusmarket.com / seller123");
            }
        };
    }
}