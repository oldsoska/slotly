package dev.slotly.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9-]+") String slug,
            @Size(max = 64) String timezone
    ) {
        String timezoneOrDefault() {
            return timezone == null || timezone.isBlank() ? "Europe/Moscow" : timezone;
        }
    }

    public record Response(Long id, String name, String slug, String timezone) {
        static Response from(Tenant t) {
            return new Response(t.getId(), t.getName(), t.getSlug(), t.getTimezone());
        }
    }
}
