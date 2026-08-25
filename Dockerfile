FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B package -DskipTests && \
    mv target/*.jar target/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home appuser
COPY --from=build /app/target/app.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
