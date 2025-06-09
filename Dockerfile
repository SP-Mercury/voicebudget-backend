FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/voicebudget-backend-0.0.1-SNAPSHOT.jar app.jar
COPY speech-key.json /app/speech-key.json

# ✅ 用 shell 啟動，以繼承環境變數
ENTRYPOINT ["sh", "-c", "java -jar app.jar"]
