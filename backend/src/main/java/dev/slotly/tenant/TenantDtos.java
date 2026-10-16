package dev.slotly.tenant;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record Response(Long id, String name, String slug, String timezone) {
        static Response from(Tenant t) {
            return new Response(t.getId(), t.getName(), t.getSlug(), t.getTimezone());
        }
    }
}
