# 0011. Container image

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

The service has to run the same way on a laptop, in CI and in production, next to the mocks of the statement. It needs a JDK to build and only a JRE to run, it must not run as root, and it must respect the memory limit of its container and stop without dropping requests.

## Decision

A multi-stage `Dockerfile` at the root of the repository, which is the build context because the API is generated from `similarProducts.yaml`:

- **Build stage** on `eclipse-temurin:25-jdk`: `./mvnw package` with a cache mount for `~/.m2`, without tests, since CI runs them before publishing an image. The jar is then extracted into Spring Boot's layers.
- **Run stage** on `eclipse-temurin:25-jre`, with the layers copied from the least to the most frequently changing (dependencies, loader, snapshots, application), so a code change only rebuilds the last one.
- Both base images are pinned by digest, so builds are reproducible and updates are explicit.
- The process runs as an unprivileged `app` user.
- `-XX:MaxRAMPercentage=75` sizes the heap from the container's memory limit, leaving room for metaspace, threads and buffers.
- A `HEALTHCHECK` calls `/actuator/health/liveness`. The image has neither `curl` nor `wget`, so bash opens the connection itself instead of adding a package.
- Graceful shutdown is Spring Boot's default: on `SIGTERM`, the server stops accepting requests and waits for the active ones to complete.
- `.dockerignore` lets in only the contract and the application sources.

## Alternatives

- **Buildpacks (`spring-boot:build-image`).** No Dockerfile to maintain, but less control over the base image and the build, and it needs the Maven build to run on the host first.
- **Alpine base image.** Smaller, but uses musl instead of glibc, a difference that brings no benefit here.
- **Copying a jar built on the host.** Faster to build, but the image then depends on the host's toolchain.

## Consequences

- `docker build .` produces a runnable image on any machine with Docker, with no JDK installed.
- Base images must be updated on purpose, by changing their digests.
- The image (about 520 MB) is dominated by the Ubuntu-based JRE.
