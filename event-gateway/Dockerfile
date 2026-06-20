FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copy all poms from root context
COPY pom.xml .
COPY account-service/pom.xml account-service/pom.xml
COPY event-gateway/pom.xml event-gateway/pom.xml

RUN mvn -pl event-gateway -am dependency:go-offline -B

# 2. Copy all code structures so multi-module tracking resolves smoothly
COPY account-service account-service
COPY event-gateway event-gateway

# 3. FIXED: Install the parent root POM metadata first (Non-recursive)
RUN mvn -N install -B

# 4. Install account-service into local container repo
RUN mvn -pl account-service install -DskipTests -B

# 5. Compile the target gateway service smoothly
RUN mvn -pl event-gateway package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/event-gateway/target/event-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]