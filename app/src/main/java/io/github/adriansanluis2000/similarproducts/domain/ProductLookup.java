package io.github.adriansanluis2000.similarproducts.domain;

import io.github.adriansanluis2000.similarproducts.domain.model.Product;

/** Outcome of looking up the detail of a single product. */
public sealed interface ProductLookup {

    record Found(Product product) implements ProductLookup {}

    record NotFound() implements ProductLookup {}

    record Unavailable(UnavailableReason reason) implements ProductLookup {}
}
