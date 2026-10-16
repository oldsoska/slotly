package dev.slotly.availability;

import dev.slotly.ApiTestSupport;
import dev.slotly.auth.AuthDtos;
import dev.slotly.booking.BookingDtos;
import dev.slotly.catalog.CatalogDtos;
import dev.slotly.schedule.ScheduleDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleAndAvailabilityTest extends ApiTestSupport {

    TestRestTemplate owner;
    Long staffId;
    Long serviceId;

    @BeforeEach
    void setUp() {
        owner = registerBusiness();
        staffId = owner.postForObject("/api/v1/staff",
                new CatalogDtos.CreateStaffRequest("Ivan Petrov"), CatalogDtos.StaffResponse.class).id();
        serviceId = owner.postForObject("/api/v1/services",
                new CatalogDtos.CreateServiceRequest("Haircut", 45, new BigDecimal("1500.00")),
                CatalogDtos.ServiceResponse.class).id();
    }

    @Test
    void freeSlotsFollowA15MinuteGridInsideWorkingHours() {
        workEveryDay(owner, staffId, "10:00", "12:00");

        // a 45-minute service must end by 12:00, so the last start is 11:15
        assertThat(freeStarts()).containsExactly(
                tomorrowAt("10:00"), tomorrowAt("10:15"), tomorrowAt("10:30"),
                tomorrowAt("10:45"), tomorrowAt("11:00"), tomorrowAt("11:15"));
    }

    @Test
    void bookedTimeDisappearsFromFreeSlots() {
        workEveryDay(owner, staffId, "10:00", "12:00");
        assertThat(tryBook(tomorrowAt("10:30"))).isEqualTo(HttpStatus.CREATED);

        // every start from 10:00 to 11:00 would overlap the 10:30-11:15 booking
        assertThat(freeStarts()).containsExactly(tomorrowAt("11:15"));
    }

    @Test
    void cancelledBookingFreesItsSlotsAgain() {
        workEveryDay(owner, staffId, "10:00", "12:00");
        Long bookingId = owner.postForObject("/api/v1/bookings", bookingRequest(tomorrowAt("10:00")),
                BookingDtos.Response.class).id();

        owner.postForObject("/api/v1/bookings/" + bookingId + "/cancel", null, String.class);

        assertThat(freeStarts()).hasSize(6);
    }

    @Test
    void bookingOutsideWorkingHoursIsRejected() {
        workEveryDay(owner, staffId, "09:00", "18:00");

        assertThat(tryBook(tomorrowAt("08:30"))).isEqualTo(HttpStatus.CONFLICT);
        // starts inside working hours but would end at 18:15
        assertThat(tryBook(tomorrowAt("17:30"))).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tryBook(tomorrowAt("17:15"))).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void dayOffHasNoSlotsAndAcceptsNoBookings() {
        var everyDayButTomorrow = Arrays.stream(DayOfWeek.values())
                .filter(day -> day != tomorrow().getDayOfWeek())
                .map(day -> new ScheduleDtos.WorkingDay(day, LocalTime.of(9, 0), LocalTime.of(18, 0)))
                .toList();
        putWeek(everyDayButTomorrow);

        assertThat(freeStarts()).isEmpty();
        assertThat(tryBook(tomorrowAt("10:00"))).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void timeOffBlocksSlotsAndBookings() {
        workEveryDay(owner, staffId, "10:00", "12:00");
        var lunch = new ScheduleDtos.CreateTimeOffRequest(tomorrowAt("11:00"), tomorrowAt("12:00"), "Dentist");
        Long timeOffId = owner.postForObject("/api/v1/staff/" + staffId + "/time-off", lunch,
                ScheduleDtos.TimeOffResponse.class).id();

        // only starts that finish by 11:00 remain
        assertThat(freeStarts()).containsExactly(tomorrowAt("10:00"), tomorrowAt("10:15"));
        assertThat(tryBook(tomorrowAt("10:30"))).isEqualTo(HttpStatus.CONFLICT);

        owner.delete("/api/v1/staff/" + staffId + "/time-off/" + timeOffId);
        assertThat(freeStarts()).hasSize(6);
    }

    @Test
    void putReplacesTheWholeWeek() {
        workEveryDay(owner, staffId, "09:00", "18:00");
        putWeek(List.of(new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(14, 0))));

        ScheduleDtos.WorkingDay[] week = owner.getForObject("/api/v1/staff/" + staffId + "/working-hours",
                ScheduleDtos.WorkingDay[].class);

        assertThat(week).containsExactly(
                new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
    }

    @Test
    void invalidWeekIsRejected() {
        var endBeforeStart = List.of(
                new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0)));
        var duplicateDay = List.of(
                new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(18, 0)));
        var missingField = List.of(Map.of("dayOfWeek", "MONDAY", "start", "09:00"));

        assertThat(putWeekStatus(endBeforeStart)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(putWeekStatus(duplicateDay)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(putWeekStatus(missingField)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyOwnerCanChangeTheSchedule() {
        String staffEmail = uniqueEmail();
        owner.postForEntity("/api/v1/staff/" + staffId + "/account",
                new AuthDtos.CreateAccountRequest(staffEmail, PASSWORD), String.class);
        TestRestTemplate staff = login(staffEmail, PASSWORD);

        var week = List.of(new ScheduleDtos.WorkingDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        HttpStatusCode status = staff.exchange("/api/v1/staff/" + staffId + "/working-hours", HttpMethod.PUT,
                new HttpEntity<>(week), String.class).getStatusCode();

        assertThat(status).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(staff.getForEntity("/api/v1/staff/" + staffId + "/working-hours", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private List<Instant> freeStarts() {
        AvailabilityDtos.Slot[] slots = owner.getForObject("/api/v1/availability?serviceId={s}&date={d}",
                AvailabilityDtos.Slot[].class, serviceId, tomorrow());
        return Arrays.stream(slots).map(AvailabilityDtos.Slot::startsAt).toList();
    }

    private HttpStatusCode tryBook(Instant startsAt) {
        return owner.postForEntity("/api/v1/bookings", bookingRequest(startsAt), String.class).getStatusCode();
    }

    private BookingDtos.CreateRequest bookingRequest(Instant startsAt) {
        return new BookingDtos.CreateRequest(staffId, serviceId, "Anna", "+79990001122", startsAt);
    }

    private void putWeek(Object week) {
        assertThat(putWeekStatus(week)).isEqualTo(HttpStatus.OK);
    }

    private HttpStatusCode putWeekStatus(Object week) {
        return owner.exchange("/api/v1/staff/" + staffId + "/working-hours", HttpMethod.PUT,
                new HttpEntity<>(week), String.class).getStatusCode();
    }
}
