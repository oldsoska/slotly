package dev.slotly.booking;

import dev.slotly.TestcontainersConfig;
import dev.slotly.catalog.CatalogDtos;
import dev.slotly.tenant.TenantDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class BookingFlowTest {

    @Autowired
    TestRestTemplate http;

    Long tenantId;
    Long staffId;
    Long serviceId;
    Instant slot;

    @BeforeEach
    void setUp() {
        String slug = "shop-" + UUID.randomUUID().toString().substring(0, 8);
        tenantId = http.postForObject("/api/v1/tenants",
                new TenantDtos.CreateRequest("Test Barbershop", slug, null), TenantDtos.Response.class).id();
        staffId = http.postForObject(base() + "/staff",
                new CatalogDtos.CreateStaffRequest("Ivan Petrov"), CatalogDtos.StaffResponse.class).id();
        serviceId = http.postForObject(base() + "/services",
                new CatalogDtos.CreateServiceRequest("Haircut", 45, new BigDecimal("1500.00")),
                CatalogDtos.ServiceResponse.class).id();
        slot = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    @Test
    void bookingEndsAfterServiceDuration() {
        BookingDtos.Response booking = book(slot);

        assertThat(booking.endsAt()).isEqualTo(slot.plus(45, ChronoUnit.MINUTES));
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void overlappingBookingIsRejected() {
        book(slot);

        assertThat(tryBook(slot.plus(30, ChronoUnit.MINUTES))).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adjacentBookingIsAllowed() {
        book(slot);

        assertThat(tryBook(slot.plus(45, ChronoUnit.MINUTES))).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void cancellingFreesTheSlot() {
        Long bookingId = book(slot).id();

        http.postForObject(base() + "/bookings/" + bookingId + "/cancel", null, BookingDtos.Response.class);

        assertThat(tryBook(slot)).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void concurrentRequestsForSameSlotProduceExactlyOneBooking() throws Exception {
        int clients = 10;
        List<Callable<HttpStatusCode>> attempts = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            attempts.add(() -> tryBook(slot));
        }

        List<HttpStatusCode> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            for (Future<HttpStatusCode> f : pool.invokeAll(attempts)) {
                results.add(f.get());
            }
        }

        assertThat(results).filteredOn(s -> s.isSameCodeAs(HttpStatus.CREATED)).hasSize(1);
        assertThat(results).filteredOn(s -> s.isSameCodeAs(HttpStatus.CONFLICT)).hasSize(clients - 1);
    }

    /** Books a slot and fails the test unless it succeeds. */
    private BookingDtos.Response book(Instant startsAt) {
        ResponseEntity<BookingDtos.Response> response =
                http.postForEntity(base() + "/bookings", bookingRequest(startsAt), BookingDtos.Response.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** Attempts a booking and returns only the status; the body may be a Problem Details error. */
    private HttpStatusCode tryBook(Instant startsAt) {
        return http.postForEntity(base() + "/bookings", bookingRequest(startsAt), String.class).getStatusCode();
    }

    private BookingDtos.CreateRequest bookingRequest(Instant startsAt) {
        return new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", startsAt);
    }

    private String base() {
        return "/api/v1/tenants/" + tenantId;
    }
}
