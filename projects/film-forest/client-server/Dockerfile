# ========== 构建阶段（在有 Maven 的机器上执行 mvn package -DskipTests）==========
# mvn clean package -DskipTests -f /root/.openclaw/workspace/projects/film-forest/backend/pom.xml

# ========== 运行阶段 ==========
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制编译好的 jar（假设在宿主机构建）
COPY target/film-forest-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
