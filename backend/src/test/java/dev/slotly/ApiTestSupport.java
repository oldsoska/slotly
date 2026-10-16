package dev.slotly;

import dev.slotly.auth.AuthDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the app on a random port against Testcontainers PostgreSQL and offers authenticated clients. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

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

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
