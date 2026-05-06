# syntax=docker/dockerfile:1.6
#
# Alethicode 判题机镜像 —— 项目内构建版本。
#
# 镜像内分两段：
#   builder：编译 judger/ C 沙箱内核 + 打 _judger Python wheel
#   final ：运行 judge_server/ Flask 服务
#
# 参考上游 QingdaoU/JudgeServer Dockerfile（commit b28aa56），按项目目录布局调整：
#   - C 内核源码位于 ./judger/
#   - Python 服务源码位于 ./judge_server/

FROM debian:trixie-slim AS builder
ARG TARGETARCH
ARG TARGETVARIANT
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

RUN --mount=type=cache,target=/var/cache/apt,id=apt-cache-builder-$TARGETARCH$TARGETVARIANT,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,id=apt-lib-builder-$TARGETARCH$TARGETVARIANT,sharing=locked \
    set -ex && \
    rm -f /etc/apt/apt.conf.d/docker-clean && \
    echo 'Binary::apt::APT::Keep-Downloaded-Packages "1";' > /etc/apt/apt.conf.d/keep-cache && \
    echo 'APT::Install-Recommends "0";' > /etc/apt/apt.conf.d/no-recommends && \
    apt-get update && \
    apt-get install -y libtool make cmake libseccomp-dev gcc python3 python3-venv

COPY judger/ /app/judger/
RUN set -ex && \
    cd /app/judger && \
    mkdir -p build && \
    cmake -S . -B build && \
    cmake --build build --parallel "$(nproc)"

RUN set -ex && \
    cd /app/judger/bindings/Python && \
    python3 -m venv .venv && \
    .venv/bin/pip3 install build && \
    .venv/bin/python3 -m build -w


FROM debian:trixie-slim
ARG TARGETARCH
ARG TARGETVARIANT
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

RUN --mount=type=cache,target=/var/cache/apt,id=apt-cache-final-$TARGETARCH$TARGETVARIANT,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,id=apt-lib-final-$TARGETARCH$TARGETVARIANT,sharing=locked \
    set -ex && \
    rm -f /etc/apt/apt.conf.d/docker-clean && \
    echo 'Binary::apt::APT::Keep-Downloaded-Packages "1";' > /etc/apt/apt.conf.d/keep-cache && \
    echo 'APT::Install-Recommends "0";' > /etc/apt/apt.conf.d/no-recommends && \
    needed="python3.12-minimal \
        python3.12-venv \
        libpython3.12-stdlib \
        libpython3.12-dev \
        golang-1.22-go \
        temurin-21-jdk \
        gcc-13 \
        g++-13 \
        nodejs \
        strace" && \
    savedAptMark="$(apt-mark showmanual) $needed" && \
    apt-get update && \
    apt-get install -y ca-certificates curl gnupg && \
    install -d -m 0755 /etc/apt/keyrings && \
    curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg && \
    printf 'Types: deb\nURIs: https://packages.adoptium.net/artifactory/deb\nSuites: bookworm\nComponents: main\nSigned-By: /etc/apt/keyrings/adoptium.gpg\n' > /etc/apt/sources.list.d/adoptium.sources && \
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg && \
    printf 'Types: deb\nURIs: https://deb.nodesource.com/node_20.x\nSuites: nodistro\nComponents: main\nSigned-By: /etc/apt/keyrings/nodesource.gpg\n' > /etc/apt/sources.list.d/nodesource.sources && \
    apt-get update && \
    apt-get install -y $needed && \
    update-alternatives --install /usr/bin/gcc gcc /usr/bin/gcc-13 13 && \
    update-alternatives --install /usr/bin/g++ g++ /usr/bin/g++-13 13 && \
    update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.12 12 && \
    update-alternatives --install /usr/bin/go go /usr/lib/go-1.22/bin/go 22 && \
    rm -rf /usr/lib/jvm/temurin-21-jdk-*/jmods && \
    rm -rf /usr/lib/jvm/temurin-21-jdk-*/lib/src.zip && \
    apt-mark auto '.*' > /dev/null && \
    apt-mark manual $savedAptMark && \
    apt-get purge -y --auto-remove

COPY --from=builder --chmod=755 --link /app/judger/output/libjudger.so /usr/lib/judger/libjudger.so
COPY --from=builder /app/judger/bindings/Python/dist/ /app/wheels/
RUN --mount=type=cache,target=/root/.cache/pip,id=pip-cache-$TARGETARCH$TARGETVARIANT \
    set -ex && \
    python3 -m venv .venv && \
    CC=gcc .venv/bin/pip3 install --compile --no-cache-dir flask gunicorn idna psutil requests && \
    .venv/bin/pip3 install /app/wheels/*.whl

COPY judge_server/ /app/
RUN set -ex && \
    chmod -R u=rwX,go=rX /app/ && \
    chmod +x /app/entrypoint.sh && \
    gcc -shared -fPIC -o /app/unbuffer.so /app/unbuffer.c && \
    useradd -u 901 -r -s /sbin/nologin -M compiler && \
    useradd -u 902 -r -s /sbin/nologin -M code && \
    useradd -u 903 -r -s /sbin/nologin -M -G code spj && \
    mkdir -p /usr/lib/judger

RUN set -ex && \
    gcc --version && \
    g++ --version && \
    python3 --version && \
    java -version && \
    node --version

HEALTHCHECK --interval=5s CMD [ "/app/.venv/bin/python3", "/app/service.py" ]
EXPOSE 8080
ENTRYPOINT [ "/app/entrypoint.sh" ]
