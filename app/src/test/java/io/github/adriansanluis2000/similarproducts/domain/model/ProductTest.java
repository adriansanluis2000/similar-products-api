package io.github.adriansanluis2000.similarproducts.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

    private static final ProductId ID = new ProductId("1");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void requiresAName(String name) {
        assertThatThrownBy(() -> new Product(ID, name, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresIdAndPrice() {
        assertThatThrownBy(() -> new Product(null, "Shirt", BigDecimal.TEN, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Product(ID, "Shirt", null, true)).isInstanceOf(NullPointerException.class);
    }
}
