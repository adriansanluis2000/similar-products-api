# syntax=docker/dockerfile:1
# See docs/adr/0011-container-image.md.

# Build: compile the application and split the jar into layers that change at different rates.
# Tests are not run here: CI runs them before an image is published.
FROM eclipse-temurin:25-jdk@sha256:2d5d5e9b533342a565907c84f47a2ec298c40cde7b13a3ca85d4950fa456a641 AS build
WORKDIR /workspace
COPY similarProducts.yaml .
COPY app app
RUN --mount=type=cache,target=/root/.m2 \
    cd app \
    && ./mvnw -B -ntp -q package -DskipTests \
    && java -Djarmode=tools -jar target/similar-products-api-0.0.1-SNAPSHOT.jar extract --layers --launcher --destination target/extracted

# Run: JRE only, as an unprivileged user. Dependencies come first, so code changes only rebuild the last layer.
FROM eclipse-temurin:25-jre@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/app/target/extracted/dependencies/ ./
COPY --from=build /workspace/app/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/app/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/app/target/extracted/application/ ./
USER app
EXPOSE 5000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# The image has no curl or wget: bash opens the connection itself.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/5000 \
        && printf "GET /actuator/health/liveness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 \
        && grep -q "\"status\":\"UP\"" <&3'
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
