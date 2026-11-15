# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml ./
COPY libs/paycore-common ./libs/paycore-common
COPY services/identity-service ./services/identity-service
COPY services/ledger-service/pom.xml ./services/ledger-service/pom.xml
COPY services/settlement-service/pom.xml ./services/settlement-service/pom.xml

RUN mvn clean package -DskipTests -pl services/identity-service -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S paycore && adduser -S paycore -G paycore

COPY --from=build /build/services/identity-service/target/*.jar app.jar

USER paycore:paycore

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
