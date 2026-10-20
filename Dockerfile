# 1. Dashboard SPA
FROM node:24-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build -- --outDir /out/app

# 2. Spring Boot jar with the SPA inside static/app
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /out/app ./src/main/resources/static/app
RUN mvn -B -q package -DskipTests

# 3. Runtime: JRE only
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 slotly
USER slotly
COPY --from=build /app/target/*.jar app.jar
# Overridable per environment, e.g. a smaller heap share on a 512 MB instance
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
