package dev.slotly.booking;

import dev.slotly.ApiTestSupport;
import dev.slotly.catalog.CatalogDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BookingFlowTest extends ApiTestSupport {

    TestRestTemplate owner;
    Long staffId;
    Long serviceId;
    Instant slot;

    @BeforeEach
    void setUp() {
        owner = registerBusiness();
        staffId = owner.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Ivan Petrov"), CatalogDtos.StaffResponse.class).id();
        serviceId = owner.postForObject("/api/v1/services",
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

        owner.postForObject("/api/v1/bookings/" + bookingId + "/cancel", null, BookingDtos.Response.class);

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
                owner.postForEntity("/api/v1/bookings", bookingRequest(startsAt), BookingDtos.Response.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** Attempts a booking and returns only the status; the body may be a Problem Details error. */
    private HttpStatusCode tryBook(Instant startsAt) {
        return owner.postForEntity("/api/v1/bookings", bookingRequest(startsAt), String.class).getStatusCode();
    }

    private BookingDtos.CreateRequest bookingRequest(Instant startsAt) {
        return new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", startsAt);
    }
}
