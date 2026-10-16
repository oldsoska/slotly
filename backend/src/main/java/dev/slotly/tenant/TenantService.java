package dev.slotly.tenant;

import dev.slotly.common.BadRequestException;
import dev.slotly.common.ConflictException;
import dev.slotly.common.Constraints;
import dev.slotly.common.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
public class TenantService {

    private static final String DEFAULT_TIMEZONE = "Europe/Moscow";

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public Tenant create(String name, String slug, String timezone) {
        if (tenants.existsBySlug(slug)) {
            throw new ConflictException("Slug '" + slug + "' is already taken");
        }
        String zone = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : validZone(timezone);
        try {
            return tenants.saveAndFlush(new Tenant(name, slug, zone));
        } catch (DataIntegrityViolationException e) {
            if (Constraints.isViolated(e, "tenant_slug_uk")) {
                throw new ConflictException("Slug '" + slug + "' is already taken");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Tenant get(Long id) {
        return tenants.findById(id).orElseThrow(() -> new NotFoundException("Tenant", id));
    }

    @Transactional(readOnly = true)
    public Tenant getBySlug(String slug) {
        return tenants.findBySlug(slug).orElseThrow(() -> new NotFoundException("Business", slug));
    }

    /** Working hours are stored in local time; this is the zone they are interpreted in. */
    @Transactional(readOnly = true)
    public ZoneId zone(Long tenantId) {
        return ZoneId.of(get(tenantId).getTimezone());
    }

    private static String validZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone '" + timezone + "'");
        }
    }
}
