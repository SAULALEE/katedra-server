FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B \
    && mv target/katedra-server-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S katedra && adduser -S katedra -G katedra
COPY --from=build /app/target/app.jar app.jar
USER katedra

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
