package com.ledgerbridge.risk.repository;

import com.ledgerbridge.risk.model.CustomerRiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRiskProfileRepository extends JpaRepository<CustomerRiskProfile, UUID> {
    Optional<CustomerRiskProfile> findByUserId(UUID userId);
}
