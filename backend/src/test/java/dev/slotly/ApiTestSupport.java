package dev.slotly;

import dev.slotly.auth.AuthDtos;
import dev.slotly.schedule.ScheduleDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the app on a random port against Testcontainers PostgreSQL and offers authenticated clients. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";
    protected static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    @Autowired
    protected TestRestTemplate anonymous;

    @Autowired
    private RestTemplateBuilder builder;

    @LocalServerPort
    private int port;

    /** Registers a fresh business and returns a client logged in as its owner. */
    protected TestRestTemplate registerBusiness() {
        return as(register(uniqueEmail()).accessToken());
    }

    protected AuthDtos.TokenResponse register(String email) {
        String slug = "shop-" + UUID.randomUUID().toString().substring(0, 8);
        var request = new AuthDtos.RegisterRequest("Test Barbershop", slug, null, email, PASSWORD);
        ResponseEntity<AuthDtos.TokenResponse> response =
                anonymous.postForEntity("/api/v1/auth/register", request, AuthDtos.TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected TestRestTemplate login(String email, String password) {
        ResponseEntity<AuthDtos.TokenResponse> response = anonymous.postForEntity(
                "/api/v1/auth/login", new AuthDtos.LoginRequest(email, password), AuthDtos.TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return as(response.getBody().accessToken());
    }

    protected TestRestTemplate as(String accessToken) {
        return new TestRestTemplate(builder
                .rootUri("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** Sets the same working hours for every day of the week. */
    protected static void workEveryDay(TestRestTemplate owner, Long staffId, String start, String end) {
        List<ScheduleDtos.WorkingDay> week = Arrays.stream(DayOfWeek.values())
                .map(day -> new ScheduleDtos.WorkingDay(day, LocalTime.parse(start), LocalTime.parse(end)))
                .toList();
        ResponseEntity<String> response = owner.exchange("/api/v1/staff/" + staffId + "/working-hours",
                HttpMethod.PUT, new HttpEntity<>(week), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Registered businesses default to this zone, so working hours are interpreted in it. */
    protected static LocalDate tomorrow() {
        return LocalDate.now(ZONE).plusDays(1);
    }

    protected static Instant tomorrowAt(String localTime) {
        return ZonedDateTime.of(tomorrow(), LocalTime.parse(localTime), ZONE).toInstant();
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
