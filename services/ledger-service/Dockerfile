# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml ./
COPY libs/paycore-common ./libs/paycore-common
COPY services/identity-service/pom.xml ./services/identity-service/pom.xml
COPY services/ledger-service ./services/ledger-service
COPY services/settlement-service/pom.xml ./services/settlement-service/pom.xml

RUN mvn clean package -DskipTests -pl services/ledger-service -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S paycore && adduser -S paycore -G paycore

COPY --from=build /build/services/ledger-service/target/*.jar app.jar

USER paycore:paycore

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8082

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
