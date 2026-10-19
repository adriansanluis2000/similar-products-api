package io.github.adriansanluis2000.similarproducts.domain.model;

public class InvalidProductIdException extends IllegalArgumentException {

    public InvalidProductIdException() {
        super("Product id must be 1 to 64 letters, digits, hyphens or underscores");
    }
}
