# =============================================================================
# Stage 1: Build the multi-module Maven application
# =============================================================================
FROM maven:3.9.9-amazoncorretto-21-alpine AS builder
WORKDIR /workspace

# Copy POM files and sources to build the executable Spring Boot fat JAR
COPY pom.xml ./
COPY cocos-trading-contract ./cocos-trading-contract
COPY cocos-trading-service ./cocos-trading-service

RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Lightweight Alpine JDK 21 runtime image
# =============================================================================
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

RUN addgroup -S cocos && adduser -S cocos -G cocos \
    && mkdir -p /app/libs \
    && chown -R cocos:cocos /app

COPY --from=builder --chown=cocos:cocos /workspace/cocos-trading-service/target/*.jar /app/libs/app.jar

USER cocos

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/libs/app.jar"]
