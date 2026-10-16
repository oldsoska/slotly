package dev.slotly.auth;

import dev.slotly.ApiTestSupport;
import dev.slotly.booking.BookingDtos;
import dev.slotly.catalog.CatalogDtos;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndAccessTest extends ApiTestSupport {

    private static final String BOOKINGS_THIS_WEEK = "/api/v1/bookings?from={from}&to={to}";

    @Test
    void registeredOwnerCanSeeOwnAccount() {
        String email = uniqueEmail();
        TestRestTemplate owner = as(register(email).accessToken());

        AuthDtos.AccountResponse me = owner.getForObject("/api/v1/me", AuthDtos.AccountResponse.class);

        assertThat(me.email()).isEqualTo(email);
        assertThat(me.role()).isEqualTo(Role.OWNER);
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() {
        String email = uniqueEmail();
        register(email);

        var duplicate = new AuthDtos.RegisterRequest("Other", "other-" + email.hashCode(), null,
                email.toUpperCase(), PASSWORD);
        assertThat(anonymous.postForEntity("/api/v1/auth/register", duplicate, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(login(email.toUpperCase(), PASSWORD).getForEntity("/api/v1/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() {
        String email = uniqueEmail();
        register(email);

        var wrongPassword = anonymous.postForEntity("/api/v1/auth/login",
                new AuthDtos.LoginRequest(email, "wrong-password"), String.class);
        var unknownEmail = anonymous.postForEntity("/api/v1/auth/login",
                new AuthDtos.LoginRequest(uniqueEmail(), "wrong-password"), String.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).isEqualTo(unknownEmail.getBody());
    }

    @Test
    void apiRequiresAValidToken() {
        assertThat(anonymous.getForEntity("/api/v1/staff", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = register(uniqueEmail()).accessToken();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(as(tampered).getForEntity("/api/v1/staff", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void staffCanReadCatalogButOnlyOwnerCanChangeIt() {
        TestRestTemplate owner = registerBusiness();
        Long staffId = owner.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Maria"), CatalogDtos.StaffResponse.class).id();
        String staffEmail = uniqueEmail();
        ResponseEntity<AuthDtos.AccountResponse> account = owner.postForEntity("/api/v1/staff/" + staffId + "/account",
                new AuthDtos.CreateAccountRequest(staffEmail, PASSWORD), AuthDtos.AccountResponse.class);
        assertThat(account.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(account.getBody().role()).isEqualTo(Role.STAFF);

        TestRestTemplate staff = login(staffEmail, PASSWORD);

        assertThat(staff.getForEntity("/api/v1/services", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var newService = new CatalogDtos.CreateServiceRequest("Shave", 30, BigDecimal.TEN);
        assertThat(staff.postForEntity("/api/v1/services", newService, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void businessesCannotSeeOrTouchEachOthersBookings() {
        TestRestTemplate shopA = registerBusiness();
        Long staffId = shopA.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Ivan"), CatalogDtos.StaffResponse.class).id();
        Long serviceId = shopA.postForObject("/api/v1/services",
                new CatalogDtos.CreateServiceRequest("Haircut", 45, BigDecimal.TEN), CatalogDtos.ServiceResponse.class).id();
        workEveryDay(shopA, staffId, "09:00", "18:00");
        Instant slot = tomorrowAt("12:00");
        Long bookingId = shopA.postForObject("/api/v1/bookings",
                new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", slot),
                BookingDtos.Response.class).id();

        TestRestTemplate shopB = registerBusiness();

        BookingDtos.Response[] visibleToB = shopB.getForObject(BOOKINGS_THIS_WEEK, BookingDtos.Response[].class,
                slot.minus(1, ChronoUnit.DAYS), slot.plus(1, ChronoUnit.DAYS));
        assertThat(visibleToB).isEmpty();
        assertThat(shopB.postForEntity("/api/v1/bookings/" + bookingId + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        var intoForeignStaff = new BookingDtos.CreateRequest(staffId, serviceId, "Eve", "+79990001133",
                slot.plus(3, ChronoUnit.HOURS));
        assertThat(shopB.postForEntity("/api/v1/bookings", intoForeignStaff, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
