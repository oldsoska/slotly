package dev.slotly.tenant;

import dev.slotly.common.ConflictException;
import dev.slotly.common.Constraints;
import dev.slotly.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public Tenant create(TenantDtos.CreateRequest request) {
        if (tenants.existsBySlug(request.slug())) {
            throw new ConflictException("Slug '" + request.slug() + "' is already taken");
        }
        try {
            return tenants.saveAndFlush(new Tenant(request.name(), request.slug(), request.timezoneOrDefault()));
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, "tenant_slug_uk")) {
                throw new ConflictException("Slug '" + request.slug() + "' is already taken");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Tenant get(Long id) {
        return tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant", id));
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!tenants.existsById(id)) {
            throw new NotFoundException("Tenant", id);
        }
    }
}
