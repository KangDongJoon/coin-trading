# 1. Gradle을 이용해 애플리케이션 빌드
FROM gradle:8.12-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

# 2. 실행 환경
FROM openjdk:17-slim
WORKDIR /app
ENV DB_USERNAME=${DB_USERNAME}
ENV DB_PASSWORD=${DB_PASSWORD}
ENV jwt_secret_key=${jwt_secret_key}
ENV origin_ip=${origin_ip}
ENV aes_secret_key=${aes_secret_key}
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EXPOSE 8080
