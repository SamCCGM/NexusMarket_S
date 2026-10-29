package application.service;

import application.persistence.mysql.entity.AdministratorEntity;
import application.persistence.mysql.repository.AdministratorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AdministratorService {

    @Autowired
    private AdministratorRepository administratorRepository;

    public AdministratorEntity save(AdministratorEntity admin) {
        return administratorRepository.save(admin);
    }

    public Optional<AdministratorEntity> findById(Long id) {
        return administratorRepository.findById(id);
    }

    public Optional<AdministratorEntity> findByUserEntityId(Long userId) {
        return administratorRepository.findByUserEntityId(userId);
    }

    public List<AdministratorEntity> findAll() {
        return administratorRepository.findAll();
    }

    public List<AdministratorEntity> findBySuperAdminTrue() {
        return administratorRepository.findBySuperAdminTrue();
    }

    public void deleteById(Long id) {
        administratorRepository.deleteById(id);
    }
}