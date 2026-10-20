package io.github.adriansanluis2000.similarproducts.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimilarProductsPropertiesTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class);

    @Test
    void bindsTheBudget() {
        context.withPropertyValues("similar-products.budget=1500ms")
                .run(it -> assertThat(
                                it.getBean(SimilarProductsProperties.class).budget())
                        .isEqualTo(Duration.ofMillis(1500)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void rejectsABudgetThatIsNotPositive(String budget) {
        context.withPropertyValues("similar-products.budget=" + budget)
                .run(it -> assertThat(it).hasFailed());
    }

    @Test
    void requiresABudget() {
        context.run(it -> assertThat(it).hasFailed());
    }

    @EnableConfigurationProperties(SimilarProductsProperties.class)
    static class PropertiesOnly {}
}
