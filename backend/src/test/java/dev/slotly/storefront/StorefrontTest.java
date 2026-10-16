package dev.slotly.storefront;

import dev.slotly.ApiTestSupport;
import dev.slotly.availability.AvailabilityDtos;
import dev.slotly.booking.BookingDtos;
import dev.slotly.booking.BookingStatus;
import dev.slotly.catalog.CatalogDtos;
import dev.slotly.tenant.TenantDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontTest extends ApiTestSupport {

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
                new CatalogDtos.CreateServiceRequest("Haircut", 45, new BigDecimal("1500.00")),
                CatalogDtos.ServiceResponse.class).id();
        workEveryDay(owner, staffId, "10:00", "12:00");
    }

    @Test
    void anyoneCanSeeTheBusinessCatalog() {
        StorefrontDtos.Business business = anonymous.getForObject("/api/v1/public/businesses/" + slug,
                StorefrontDtos.Business.class);

        assertThat(business.name()).isEqualTo("Test Barbershop");
        assertThat(business.timezone()).isEqualTo("Europe/Moscow");
        assertThat(business.services()).extracting(StorefrontDtos.Service::name).containsExactly("Haircut");
        assertThat(business.staff()).extracting(StorefrontDtos.Master::fullName).containsExactly("Ivan Petrov");
    }

    @Test
    void unknownBusinessIs404() {
        assertThat(anonymous.getForEntity("/api/v1/public/businesses/no-such-shop", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void clientBooksWithoutAnAccountAndTheOwnerSeesIt() {
        AvailabilityDtos.Slot[] slots = anonymous.getForObject(
                "/api/v1/public/businesses/{slug}/availability?serviceId={s}&date={d}",
                AvailabilityDtos.Slot[].class, slug, serviceId, tomorrow());
        assertThat(slots).hasSize(6);

        ResponseEntity<StorefrontDtos.Confirmation> booked = book(slots[0].startsAt());

        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        StorefrontDtos.Confirmation confirmation = booked.getBody();
        assertThat(confirmation.cancelToken()).isNotNull();
        assertThat(confirmation.serviceName()).isEqualTo("Haircut");
        assertThat(confirmation.staffName()).isEqualTo("Ivan Petrov");
        assertThat(confirmation.status()).isEqualTo(BookingStatus.CONFIRMED);

        BookingDtos.Response[] ownerView = owner.getForObject("/api/v1/bookings?from={f}&to={t}",
                BookingDtos.Response[].class, tomorrowAt("00:00"), tomorrowAt("23:59"));
        assertThat(ownerView).extracting(BookingDtos.Response::clientName).containsExactly("Anna");
    }

    @Test
    void sameRulesApplyAsForStaff() {
        book(tomorrowAt("10:00"));

        assertThat(tryBook(tomorrowAt("10:30"))).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tryBook(tomorrowAt("09:00"))).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmationLinkShowsAndCancelsTheBooking() {
        UUID token = book(tomorrowAt("10:00")).getBody().cancelToken();

        StorefrontDtos.Confirmation viewed = anonymous.getForObject("/api/v1/public/bookings/" + token,
                StorefrontDtos.Confirmation.class);
        assertThat(viewed.startsAt()).isEqualTo(tomorrowAt("10:00"));

        StorefrontDtos.Confirmation cancelled = anonymous.postForObject(
                "/api/v1/public/bookings/" + token + "/cancel", null, StorefrontDtos.Confirmation.class);
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(tryBook(tomorrowAt("10:00"))).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void unknownTokenIs404() {
        HttpStatusCode status = anonymous.postForEntity(
                "/api/v1/public/bookings/" + UUID.randomUUID() + "/cancel", null, String.class).getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotBookAnotherBusinessesStaffThroughThisPage() {
        TestRestTemplate otherOwner = registerBusiness();
        Long foreignStaff = otherOwner.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Stranger"), CatalogDtos.StaffResponse.class).id();
        workEveryDay(otherOwner, foreignStaff, "10:00", "12:00");

        var request = new BookingDtos.CreateRequest(foreignStaff, serviceId, "Eve", "+79990001133", tomorrowAt("10:00"));
        HttpStatusCode status = anonymous.postForEntity("/api/v1/public/businesses/" + slug + "/bookings",
                request, String.class).getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelTokenIsNotLeakedToStaffEndpoints() {
        book(tomorrowAt("10:00"));

        String ownerView = owner.getForObject("/api/v1/bookings?from={f}&to={t}", String.class,
                tomorrowAt("00:00"), tomorrowAt("23:59").plus(1, ChronoUnit.MINUTES));

        assertThat(ownerView).doesNotContain("cancelToken");
    }

    @Test
    void bookingPageIsServedForAnyBusinessLink() {
        ResponseEntity<String> page = anonymous.getForEntity("/b/" + slug, String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(page.getBody()).contains("/booking/booking.js");
        assertThat(anonymous.getForEntity("/booking/booking.js", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<StorefrontDtos.Confirmation> book(Instant startsAt) {
        var request = new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", startsAt);
        return anonymous.postForEntity("/api/v1/public/businesses/" + slug + "/bookings", request,
                StorefrontDtos.Confirmation.class);
    }

    /** Status only: an error response is Problem Details, not a confirmation. */
    private HttpStatusCode tryBook(Instant startsAt) {
        var request = new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", startsAt);
        return anonymous.postForEntity("/api/v1/public/businesses/" + slug + "/bookings", request, String.class)
                .getStatusCode();
    }
}
