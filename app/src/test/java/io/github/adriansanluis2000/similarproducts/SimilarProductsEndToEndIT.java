package io.github.adriansanluis2000.similarproducts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.adriansanluis2000.similarproducts.infrastructure.adapter.in.rest.model.ProductDetail;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The scenarios of the load test, end to end: the application over HTTP, against the mock image and the mocks.json of
 * the statement. Skipped where Docker is not available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class SimilarProductsEndToEndIT {

    @Container
    static final GenericContainer<?> simulado = new GenericContainer<>(DockerImageName.parse(
                    "ldabiralai/simulado@sha256:e1a3287457a4d1978fc0c2de80756a3b8afb39039a38cb8196c0295943208e3c"))
            .withCopyFileToContainer(MountableFile.forHostPath("../shared/simulado/mocks.json"), "/app/mocks.json")
            .withCommand("./bin/simulado", "-f", "/app/mocks.json")
            .withExposedPorts(80)
            .waitingFor(Wait.forHttp("/product/1/similarids").forStatusCode(200));

    @DynamicPropertySource
    static void existingApis(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.http.serviceclient.products.base-url",
                () -> "http://" + simulado.getHost() + ":" + simulado.getMappedPort(80));
    }

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void createClient() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void normalReturnsAllSimilarProducts() {
        assertThat(similarIds("1")).containsExactly("2", "3", "4");
    }

    @Test
    void notFoundLeavesOutTheProductThatDoesNotExist() {
        assertThat(similarIds("4")).containsExactly("1", "2");
    }

    @Test
    void errorLeavesOutTheProductThatFails() {
        assertThat(similarIds("5")).containsExactly("1", "2");
    }

    @Test
    void slowAnswersWithinTheBudgetAndAddsTheSlowProductsOnceTheyArrive() {
        var started = System.nanoTime();
        assertThat(similarIds("2")).startsWith("3");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_500));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(similarIds("2")).containsExactly("3", "100", "1000"));
    }

    @Test
    void verySlowNeverWaitsForTheProductThatTakes50Seconds() {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(similarIds("3")).containsExactly("100", "1000"));
    }

    @Test
    void unknownProductAnswersNotFound() {
        var status = client.get()
                .uri("/product/{productId}/similar", "99")
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private List<String> similarIds(String productId) {
        return client
                .get()
                .uri("/product/{productId}/similar", productId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ProductDetail>>() {})
                .stream()
                .map(ProductDetail::getId)
                .toList();
    }
}
