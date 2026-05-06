SHELL := /bin/bash

IMAGE_REPO ?= alethicode/judge-server
IMAGE_TAG  ?= dev
IMAGE      := $(IMAGE_REPO):$(IMAGE_TAG)
COMPOSE    := docker compose -f ../../deploy/docker-compose.judge-only.yml

.PHONY: help dev test image compose-up compose-down clean lint

help:
	@echo "Alethicode judge-server 开发命令"
	@echo ""
	@echo "  make dev           创建本地虚拟环境并安装开发依赖"
	@echo "  make test          运行 host 上可执行的 pytest 单测"
	@echo "  make lint          ruff check"
	@echo "  make image         构建镜像 $(IMAGE)（含 judger C 内核 + Flask 服务）"
	@echo "  make compose-up    启动 docker-compose.judge-only.yml（仅判题机）"
	@echo "  make compose-down  停止并清理 compose 容器"
	@echo "  make clean         清掉 build/ 与 __pycache__/"

dev:
	python3 -m venv .venv
	./.venv/bin/pip install --upgrade pip
	./.venv/bin/pip install -e ".[dev]"

test:
	./.venv/bin/python -m pytest

lint:
	./.venv/bin/python -m ruff check .

image:
	docker build -t $(IMAGE) .

compose-up:
	$(COMPOSE) up -d

compose-down:
	$(COMPOSE) down -v

clean:
	rm -rf build/ dist/ .pytest_cache/ .ruff_cache/ .coverage
	find . -type d -name __pycache__ -prune -exec rm -rf {} +
	rm -rf judger/build judger/output judger/cmake-build-debug
