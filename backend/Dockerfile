FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY .mvn .mvn
COPY pom.xml checkstyle.xml ./
COPY src src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 app && mkdir /app && chown app:app /app
WORKDIR /app
ENV PORT=8080
COPY --from=build --chown=app:app /build/target/backend-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
# Reserve aggregate container memory for the API plus two bounded parser JVMs.
ENTRYPOINT ["java", "-Xmx256m", "-XX:MaxMetaspaceSize=192m", "-XX:ReservedCodeCacheSize=64m", "-XX:MaxDirectMemorySize=64m", "-XX:ActiveProcessorCount=2", "-jar", "app.jar"]
