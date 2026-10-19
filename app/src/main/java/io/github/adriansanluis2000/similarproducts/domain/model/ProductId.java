package io.github.adriansanluis2000.similarproducts.domain.model;

import java.util.regex.Pattern;

/**
 * Identifier of a product: 1 to 64 letters, digits, hyphens or underscores. The format keeps ids safe to embed in
 * upstream URLs, with no room for path segments such as {@code ..}.
 */
public record ProductId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public ProductId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidProductIdException();
        }
    }
}
