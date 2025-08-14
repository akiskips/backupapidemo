# MIT License
# Copyright (c) 2025 AKS Backup Project
# See LICENSE file for details

# Build stage
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -B -U dependency:resolve
COPY src ./src
RUN mvn -q -e -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/java-adhoc-backup-1.0.0-jar-with-dependencies.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
