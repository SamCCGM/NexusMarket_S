package application.config;

import application.persistence.mysql.entity.UserEntity;
import application.persistence.mysql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return User.builder()
                .username(userEntity.getEmail())
                .password(userEntity.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name())))
                .accountExpired(false)
                .accountLocked(userEntity.getStatus() == application.domain.enums.UserStatus.BLOCKED)
                .credentialsExpired(false)
                .disabled(userEntity.getStatus() != application.domain.enums.UserStatus.ACTIVE)
                .build();
    }
}