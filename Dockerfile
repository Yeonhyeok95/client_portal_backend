# ---- 1단계: 빌드 (JDK 포함 이미지에서 bootJar 생성) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 의존성 캐시 레이어: 빌드 스크립트만 먼저 복사해 두면
# 소스만 바뀐 재배포에서 의존성 다운로드를 건너뛴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# ---- 2단계: 실행 (JRE만 포함한 가벼운 이미지) ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
