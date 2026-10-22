FROM docker.io/library/eclipse-temurin:26-jdk-jammy AS builder
WORKDIR /app
COPY security/blocked-certificates.properties /tmp/blocked-certificates.properties
RUN printf '\n' >> "$JAVA_HOME/lib/security/blocked.certs" \
    && cat /tmp/blocked-certificates.properties >> "$JAVA_HOME/lib/security/blocked.certs" \
    && rm /tmp/blocked-certificates.properties
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY . .

RUN ./gradlew test bootJar --no-daemon

FROM docker.io/library/eclipse-temurin:26-jre-jammy
WORKDIR /app
COPY security/blocked-certificates.properties /tmp/blocked-certificates.properties
RUN printf '\n' >> "$JAVA_HOME/lib/security/blocked.certs" \
    && cat /tmp/blocked-certificates.properties >> "$JAVA_HOME/lib/security/blocked.certs" \
    && rm /tmp/blocked-certificates.properties
COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p /app/secrets

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
