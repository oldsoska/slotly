package dev.slotly.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CreateStaffRequest(@NotBlank @Size(max = 200) String fullName) {
    }

    public record StaffResponse(Long id, String fullName, boolean active) {
        static StaffResponse from(Staff s) {
            return new StaffResponse(s.getId(), s.getFullName(), s.isActive());
        }
    }

    public record CreateServiceRequest(
            @NotBlank @Size(max = 200) String name,
            @Min(5) @Max(600) int durationMinutes,
            @NotNull @DecimalMin("0.00") BigDecimal price
    ) {
    }

    public record ServiceResponse(Long id, String name, int durationMinutes, BigDecimal price, boolean active) {
        static ServiceResponse from(ServiceOffering s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDurationMinutes(), s.getPrice(), s.isActive());
        }
    }
}
