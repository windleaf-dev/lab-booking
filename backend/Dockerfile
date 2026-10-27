# syntax=docker/dockerfile:1

# ---------- 构建阶段 ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先只拷 pom，利用 Docker 层缓存：依赖没变时不会重复下载
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S lab && adduser -S lab -G lab
COPY --from=build /app/target/lab-booking-backend-1.0.0.jar app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

USER lab
EXPOSE 8091

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
