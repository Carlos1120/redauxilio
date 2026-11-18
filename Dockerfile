# Construye el monolito Spring Boot y lo ejecuta con Java 21 en Render.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 redauxilio
COPY --from=build /workspace/target/redauxilio-*.jar /app/redauxilio.jar
USER redauxilio
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/redauxilio.jar"]
