package application.service;

import application.domain.enums.UserStatus;
import application.exception.BusinessRuleException;
import application.exception.ResourceNotFoundException;
import application.persistence.mysql.entity.UserEntity;
import application.persistence.mysql.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserEntity registerUser(UserEntity user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<UserEntity> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<UserEntity> authenticate(String email, String password) {
        return userRepository.findByEmailAndPassword(email, password);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public List<UserEntity> findByRole(String role) {
        return userRepository.findByRole(role);
    }

    public List<UserEntity> findByStatus(String status) {
        return userRepository.findByStatus(status);
    }

    public UserEntity updateUser(UserEntity user) {
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * UpdateUser (Actor: Administrator).
     * Actualiza la información general permitida de un usuario existente.
     *
     * Reglas de negocio:
     * - El usuario debe existir.
     * - El correo electrónico debe ser único (si cambia).
     * - No se modifica aquí el rol ni el estado; el estado se gestiona con
     *   BlockUser/ActivateUser para no perder el historial del usuario.
     */
    public UserEntity updateUser(Long userId, UserEntity updates) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (updates.getFullName() != null) {
            user.setFullName(updates.getFullName());
        }
        if (updates.getPhoneNumber() != null) {
            user.setPhoneNumber(updates.getPhoneNumber());
        }
        // El correo es único: solo se valida cuando se intenta cambiar.
        if (updates.getEmail() != null && !updates.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updates.getEmail())) {
                throw new BusinessRuleException("Email is already in use: " + updates.getEmail());
            }
            user.setEmail(updates.getEmail());
        }

        return userRepository.save(user);
    }

    /**
     * BlockUser (Actor: Administrator).
     * Bloquea a un usuario para impedir que participe en operaciones que
     * requieran una cuenta activa.
     *
     * Reglas de negocio:
     * - El usuario debe existir y no estar ya bloqueado.
     * - El bloqueo no borra el historial ni los pedidos anteriores:
     *   únicamente cambia el estado.
     */
    public UserEntity blockUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessRuleException("User is already blocked: " + userId);
        }

        user.setStatus(UserStatus.BLOCKED);
        return userRepository.save(user);
    }

    /**
     * ActivateUser (Actor: Administrator).
     * Reactiva a un usuario previamente bloqueado.
     *
     * Reglas de negocio:
     * - El usuario debe existir y estar bloqueado (no se reactiva uno ya activo).
     */
    public UserEntity activateUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("User is already active: " + userId);
        }

        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}