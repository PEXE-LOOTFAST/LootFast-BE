FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S lootfast && adduser -S lootfast -G lootfast
COPY --from=build --chown=lootfast:lootfast /app/target/*.jar app.jar
USER lootfast:lootfast
EXPOSE 10000
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-Djava.net.preferIPv4Addresses=true", "-jar", "app.jar"]
