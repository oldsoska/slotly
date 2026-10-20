package dev.slotly.ratelimit;

import dev.slotly.ApiTestSupport;
import dev.slotly.auth.AuthDtos;
import dev.slotly.booking.BookingDtos;
import dev.slotly.catalog.CatalogDtos;
import dev.slotly.storefront.StorefrontDtos;
import dev.slotly.tenant.TenantDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs with low limits. Requests carry X-Forwarded-For: the test client connects from 127.0.0.1, a trusted
 * proxy for Tomcat's RemoteIpValve, so the header decides the client address — as behind Render's proxy.
 * Every test uses its own random addresses and its own business, so buckets never leak between tests.
 */
@TestPropertySource(properties = {
        "slotly.rate-limit.login-per-ip.requests=5",
        "slotly.rate-limit.login-per-email.requests=3",
        "slotly.rate-limit.public-booking-per-ip.requests=3",
        "slotly.rate-limit.public-booking-per-business.requests=5",
        "slotly.rate-limit.public-cancel-per-ip.requests=3",
})
class RateLimitTest extends ApiTestSupport {

    TestRestTemplate owner;
    String slug;
    Long staffId;
    Long serviceId;

    @BeforeEach
    void setUp() {
        owner = registerBusiness();
        slug = owner.getForObject("/api/v1/tenant", TenantDtos.Response.class).slug();
        staffId = owner.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Ivan Petrov"), CatalogDtos.StaffResponse.class).id();
        serviceId = owner.postForObject("/api/v1/services",
                new CatalogDtos.CreateServiceRequest("Haircut", 30, BigDecimal.TEN), CatalogDtos.ServiceResponse.class).id();
        workEveryDay(owner, staffId, "09:00", "18:00");
    }

    @Test
    void publicBookingIsLimitedPerClientIp() {
        String ip = randomIp();

        for (int i = 0; i < 3; i++) {
            assertThat(book(ip, uniquePhone(), "1" + i + ":00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<String> limited = book(ip, uniquePhone(), "14:00");

        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(limited.getBody()).contains("\"status\":429");
        // another client is unaffected
        assertThat(book(randomIp(), uniquePhone(), "15:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void prependingFakeAddressesToForwardedForDoesNotEscapeTheLimit() {
        String realIp = randomIp();

        for (int i = 0; i < 3; i++) {
            book(randomIp() + ", " + realIp, uniquePhone(), "1" + i + ":00");
        }

        assertThat(book(randomIp() + ", " + realIp, uniquePhone(), "14:00").getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void publicBookingIsLimitedPerBusinessEvenFromManyAddresses() {
        for (int i = 0; i < 5; i++) {
            assertThat(book(randomIp(), uniquePhone(), "1" + i + ":00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        assertThat(book(randomIp(), uniquePhone(), "16:00").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void onePhoneNumberCannotHoldMoreThanThreeUpcomingBookings() {
        String phone = uniquePhone();
        StorefrontDtos.Confirmation first = null;
        for (int i = 0; i < 3; i++) {
            ResponseEntity<StorefrontDtos.Confirmation> booked = anonymous.exchange(bookingsUrl(), HttpMethod.POST,
                    bookingEntity(randomIp(), phone, "1" + i + ":00"), StorefrontDtos.Confirmation.class);
            assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            if (first == null) {
                first = booked.getBody();
            }
        }

        assertThat(book(randomIp(), phone, "14:00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        anonymous.postForObject("/api/v1/public/bookings/" + first.cancelToken() + "/cancel", null, String.class);
        assertThat(book(randomIp(), phone, "14:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void loginIsLimitedPerEmailAcrossAddresses() {
        String email = uniqueEmail();
        register(email);

        for (int i = 0; i < 3; i++) {
            assertThat(login(randomIp(), email, "wrong-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // even the right password waits now: guessing has to slow down regardless of the source
        assertThat(login(randomIp(), email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void loginIsLimitedPerClientIpAcrossAccounts() {
        String ip = randomIp();

        for (int i = 0; i < 5; i++) {
            login(ip, uniqueEmail(), "wrong-password");
        }

        assertThat(login(ip, uniqueEmail(), "wrong-password").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<String> book(String forwardedFor, String phone, String localTime) {
        return anonymous.exchange(bookingsUrl(), HttpMethod.POST, bookingEntity(forwardedFor, phone, localTime),
                String.class);
    }

    private HttpEntity<BookingDtos.CreateRequest> bookingEntity(String forwardedFor, String phone, String localTime) {
        Instant startsAt = tomorrowAt(localTime);
        return new HttpEntity<>(new BookingDtos.CreateRequest(staffId, serviceId, "Anna", phone, startsAt),
                forwardedFor(forwardedFor));
    }

    private ResponseEntity<String> login(String forwardedFor, String email, String password) {
        return anonymous.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(new AuthDtos.LoginRequest(email, password), forwardedFor(forwardedFor)), String.class);
    }

    private String bookingsUrl() {
        return "/api/v1/public/businesses/" + slug + "/bookings";
    }

    private static HttpHeaders forwardedFor(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", value);
        return headers;
    }

    /** A random public address; outside the internal ranges the valve treats as proxies, so it counts as the client. */
    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203." + r.nextInt(1, 255) + "." + r.nextInt(1, 255) + "." + r.nextInt(1, 255);
    }

    private static String uniquePhone() {
        return "+7999" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
    }
}
