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
# Tuned for the 512 MB free-tier instance. Without these the JVM takes its container
# default of MaxRAMPercentage=25, leaving a ~128 MB heap for an app that loads Tika, POI,
# PDFBox and Spring AI — enough to thrash the collector or get OOM-killed outright.
# SerialGC is the right collector for one vCPU and a small heap: G1's bookkeeping never
# pays for itself at this size. TieredStopAtLevel=1 caps JIT warm-up, trading steady-state
# throughput for a faster cold start — the correct trade for an instance that restarts
# more often than it saturates.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseSerialGC", \
    "-XX:TieredStopAtLevel=1", \
    "-Xss512k", \
    "-jar", "app.jar"]
