FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY contracts contracts
COPY frontend/src frontend/src
COPY scripts scripts
COPY start.sh start.sh
RUN apt-get update && apt-get install -y maven ripgrep && rm -rf /var/lib/apt/lists/*
RUN mvn -f backend/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar /app/app.jar

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip libreoffice-core libreoffice-writer libreoffice-impress && \
    python3 -m pip install --no-cache-dir \
        --index-url https://mirrors.aliyun.com/pypi/simple/ \
        --trusted-host mirrors.aliyun.com \
        --timeout 120 \
        --retries 5 \
        pypdf==6.9.2 python-pptx==1.0.2 python-docx==1.2.0 && \
    rm -rf /var/lib/apt/lists/*

COPY scripts/ /app/scripts/
COPY backend/scripts/ /app/scripts/

ENV JAVA_OPTS="-Xms128m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseCompressedOops"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
