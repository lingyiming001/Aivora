FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY aivora/ .
RUN mvn -q -pl aivora-admin -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/aivora-admin/target/aivora-admin-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
