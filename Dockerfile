FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=build/libs/StockForecasting-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /app/StockForecasting.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/StockForecasting.jar"]
