FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copy the root pom and only the account service pom
COPY pom.xml .
COPY account-service/pom.xml account-service/pom.xml

RUN mvn -pl account-service -am dependency:go-offline -B

# 2. Only copy the account service source directory
COPY account-service account-service

# 3. Package only the account service
RUN mvn -pl account-service package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/account-service/target/account-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]