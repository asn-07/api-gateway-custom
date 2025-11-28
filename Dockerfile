# Stage 1: Builder
FROM maven:3.9.5-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy parent pom to install it first
COPY ../pom.xml ./parent-pom.xml
RUN mvn install:install-file -Dfile=./parent-pom.xml -DgroupId=com.leo.server -DartifactId=leo-java-server -Dversion=0.0.1-SNAPSHOT -Dpackaging=pom

# Copy and build the module
COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Install curl for health check
RUN apk add --no-cache curl

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

#ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "app.jar", "--spring.profiles.active=docker-local"]
ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "app.jar", "--spring.profiles.active=docker-deployment"]