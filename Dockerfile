# 使用官方的 OpenJDK 17 基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制应用 JAR 包
COPY ./k8s-mutating-webhook-0.0.1-SNAPSHOT.jar /app/app.jar

# 暴露 HTTPS 端口
EXPOSE 8443

# 设置环境变量（可在运行时覆盖）
ENV JAVA_OPTS="-Xmx512m"

# 启动应用
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]