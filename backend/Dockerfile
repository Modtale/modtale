FROM docker.io/library/eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY . .

RUN ./gradlew bootJar --no-daemon

FROM docker.io/library/eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p /app/secrets

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]