# ============================================================================
# Build stage — Java + Maven
#
# 关键：layer 顺序按"变动频率从低到高"排 + 使用 BuildKit `--mount=type=cache`
# 让 maven `~/.m2` 跨 layer 持久化。两个机制配合：
#
# 1. layer 顺序：apt install / pom.xml / src 三段独立，src 变化只 invalidate
#    最后那段，前面 layer 全 cache hit。
# 2. cache mount：maven 的 `~/.m2/repository` 不在镜像 layer 里，而是 BuildKit
#    维护的 daemon 级别 cache 目录，跨 build 持久（即使 layer invalidate 重跑
#    也用同一份 cache），第二次 mvn package 不用重新 resolve / download 依赖。
#
# 历史教训（5/3）：之前用 `dependency:go-offline` 反而把 spring-boot 间接
# 依赖（google-cloud / aws-redshift 等几千个 BOM）也拉了，10+ 分钟还没完，
# 已撤回。直接 `mvn package` + cache mount 是最简最快路径。
# ============================================================================
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# ① 系统工具（几乎不变）
RUN sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        -e 's|http://security.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends maven ripgrep && \
    rm -rf /var/lib/apt/lists/*

RUN printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>aliyun-public</id>' \
      '      <name>Aliyun Maven Public</name>' \
      '      <url>https://maven.aliyun.com/repository/public</url>' \
      '      <mirrorOf>*</mirrorOf>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /workspace/maven-settings.xml

# ② 业务源码 + schema + maven-antrun-plugin 依赖的 scripts/m12（pom.xml validate
#    阶段调 ../scripts/m12/guard_no_api_v1.sh 守 API v1 不出现）
COPY backend/pom.xml backend/pom.xml
COPY scripts scripts
COPY backend/src backend/src
COPY contracts contracts

# ③ 编译 + 打包（cache mount 让本地仓库跨 build 持久）
RUN --mount=type=cache,target=/root/.m2/repository,id=alethicode-backend-m2 \
    mvn -s /workspace/maven-settings.xml -f backend/pom.xml -B -DskipTests package

# ============================================================================
# Runtime stage — JRE + LibreOffice + Python (PDF/PPT/DOCX 处理)
#
# 把"基本不变的系统依赖 + pip wheel"放在最前面；最后才 COPY 经常
# 变化的 jar 与 scripts，让 jar 变化时不触发重装 libreoffice (40MB) + pip。
# ============================================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ① 系统依赖 + pip wheel（基本不变）
RUN sed -i \
        -e 's|http://archive.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        -e 's|http://security.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 python3-pip libreoffice-core libreoffice-writer libreoffice-impress && \
    python3 -m pip install --no-cache-dir \
        --index-url https://mirrors.aliyun.com/pypi/simple/ \
        --trusted-host mirrors.aliyun.com \
        --timeout 120 \
        --retries 5 \
        pypdf==6.9.2 python-pptx==1.0.2 python-docx==1.2.0 && \
    rm -rf /var/lib/apt/lists/*

# ② 业务 jar + 脚本（每次代码改动都变）
COPY --from=build /workspace/backend/target/*.jar /app/app.jar
COPY scripts/ /app/scripts/
COPY backend/scripts/ /app/scripts/

ENV JAVA_OPTS="-Xms128m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseCompressedOops"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
