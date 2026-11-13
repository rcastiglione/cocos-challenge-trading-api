FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

RUN mkdir -p libs

COPY cocos-trading-service/target/*.jar ./libs/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/libs/app.jar"]

