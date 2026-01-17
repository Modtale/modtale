FROM gradle:jdk21-jammy AS builder
WORKDIR /app
COPY . .

RUN sed -i 's/\r$//' gradlew

RUN chmod +x ./gradlew

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p /app/secrets

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]