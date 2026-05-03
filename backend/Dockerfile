# ============================================================================
# Build stage — Java + Maven
#
# 关键：layer 顺序必须按"变动频率从低到高"排，让 docker 能复用 cache。
# 之前的写法把 backend/src 放在 apt install / mvn 之前，src 一变 → apt + mvn
# 全部 invalidate，rebuild 必跑 7-10 分钟拉完整套依赖。本顺序下，仅改 src
# 的 rebuild 只触发 mvn package + 重打 jar 两个 layer，~30-60 秒完成。
# ============================================================================
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# ① 系统工具（几乎不变）— 最早 layer
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven ripgrep && \
    rm -rf /var/lib/apt/lists/*

# ② pom.xml 单独 COPY 后预拉所有 maven 依赖与插件（依赖清单变化频率远低于 src）
#   — pom.xml 不变时，下面这条 RUN 全部 cache hit，ECS 不需要重拉 sentry/jackson 等
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -B \
        dependency:go-offline \
        dependency:resolve-plugins

# ③ 业务源码 + schema（变动最频繁）— 最后 layer
#   — src 改一行 → 仅本步骤后的 layer invalidate，maven 缓存不动
COPY backend/src backend/src
COPY contracts contracts
RUN mvn -f backend/pom.xml -B -Dmaven.test.skip=true package

# ============================================================================
# Runtime stage — JRE + LibreOffice + Python (PDF/PPT/DOCX 处理)
#
# 同理，把"基本不变的系统依赖 + pip wheel"放在最前面；最后才 COPY 经常
# 变化的 jar 与 scripts，让 jar 变化时不触发重装 libreoffice (40MB) + pip。
# ============================================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ① 系统依赖 + pip wheel（基本不变）
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

# ② 业务 jar + 脚本（每次代码改动都变）— 最后 layer
COPY --from=build /workspace/backend/target/*.jar /app/app.jar
COPY scripts/ /app/scripts/
COPY backend/scripts/ /app/scripts/

ENV JAVA_OPTS="-Xms128m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseCompressedOops"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
