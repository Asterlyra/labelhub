FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY Backend/maven-settings.xml /root/.m2/settings.xml
COPY Backend/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY Backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
