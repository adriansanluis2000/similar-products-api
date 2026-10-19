# 0002. Generate the API from the OpenAPI contract

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

The front-end teams agreed on `similarProducts.yaml` as the contract of the new operation. A hand-written controller can drift from it silently: a renamed path variable, a changed field name or a missing required property would only show up when a consumer breaks.

## Decision

We generate the API interface and its models from `similarProducts.yaml` at build time with `openapi-generator-maven-plugin`, using the `spring` generator for Spring Boot 4 and Jackson 3.

- `interfaceOnly` with `skipDefaultInterface`: the build generates an interface with no default implementation, so the controller must implement it or the code does not compile.
- The generated code lives in the REST adapter package (`infrastructure.adapter.in.rest`), since it is an HTTP concern. The domain never sees it.
- Generated sources stay in `target/` and are not committed. The contract file is the single source of truth.
- No Swagger annotations, springdoc or `jackson-databind-nullable`: the contract already documents the API, and none of its fields are nullable.

## Alternatives

- **Hand-written controller and DTOs, plus a test that validates responses against the YAML.** No build plugin, but drift is caught only by the cases the test covers, and the contract is duplicated in code.
- **Code-first with springdoc.** The contract would be derived from the code, the opposite of what was agreed with the consumers.

## Consequences

- Any change to the contract shows up as a compilation error in the adapter that must follow it.
- `uniqueItems: true` makes the generator return `Set<ProductDetail>`. Returning a `LinkedHashSet` keeps the similarity order while removing duplicates.
- The build depends on the generator supporting our Spring Boot version, which is worth checking on every upgrade.
