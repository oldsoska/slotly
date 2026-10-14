package dev.slotly.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByTenantIdOrderByName(Long tenantId);

    Optional<ServiceOffering> findByIdAndTenantId(Long id, Long tenantId);
}
