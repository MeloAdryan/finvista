# Etapa 1: compilar o FinVista
FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests


# Etapa 2: executar o FinVista
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/finvista-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]