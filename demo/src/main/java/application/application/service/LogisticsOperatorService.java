package application.service;

import application.persistence.mysql.entity.LogisticsOperatorEntity;
import application.persistence.mysql.repository.LogisticsOperatorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LogisticsOperatorService {

    @Autowired
    private LogisticsOperatorRepository logisticsOperatorRepository;

    public LogisticsOperatorEntity save(LogisticsOperatorEntity operator) {
        return logisticsOperatorRepository.save(operator);
    }

    public Optional<LogisticsOperatorEntity> findById(Long id) {
        return logisticsOperatorRepository.findById(id);
    }

    public Optional<LogisticsOperatorEntity> findByUserEntityId(Long userId) {
        return logisticsOperatorRepository.findByUserEntityId(userId);
    }

    public List<LogisticsOperatorEntity> findAll() {
        return logisticsOperatorRepository.findAll();
    }

    public List<LogisticsOperatorEntity> findByAvailableTrue() {
        return logisticsOperatorRepository.findByAvailableTrue();
    }

    public List<LogisticsOperatorEntity> findByCompanyNameContainingIgnoreCase(String companyName) {
        return logisticsOperatorRepository.findByCompanyNameContainingIgnoreCase(companyName);
    }

    public void deleteById(Long id) {
        logisticsOperatorRepository.deleteById(id);
    }
}