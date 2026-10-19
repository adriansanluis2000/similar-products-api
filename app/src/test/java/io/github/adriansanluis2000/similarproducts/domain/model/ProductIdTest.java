package io.github.adriansanluis2000.similarproducts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "10000", "AbC-12_x"})
    void acceptsLettersDigitsHyphensAndUnderscores(String value) {
        assertThat(new ProductId(value).value()).isEqualTo(value);
    }

    @Test
    void acceptsUpTo64Characters() {
        assertThat(new ProductId("a".repeat(64)).value()).hasSize(64);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1 2", "..", "1/2", "1?x=2", "%2F", "ñ"})
    void rejectsValuesOutsideTheAllowedFormat(String value) {
        assertThatThrownBy(() -> new ProductId(value)).isInstanceOf(InvalidProductIdException.class);
    }

    @Test
    void rejectsMoreThan64Characters() {
        assertThatThrownBy(() -> new ProductId("a".repeat(65))).isInstanceOf(InvalidProductIdException.class);
    }
}
