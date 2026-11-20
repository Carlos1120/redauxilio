# Construye el monolito Spring Boot y lo ejecuta con Java 21 en Render.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw
COPY src src
RUN mvn -B -DskipTests package

# La imagen de ejecución solo necesita Java; Maven queda en la etapa de compilación.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 redauxilio
COPY --from=build /workspace/target/redauxilio-*.jar /app/redauxilio.jar
USER redauxilio
EXPOSE 8080
# En Render Free (512 MB), reservar RAM para metadatos, hilos y memoria nativa.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=45", "-jar", "/app/redauxilio.jar"]
