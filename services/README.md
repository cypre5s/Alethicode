# services/

Python / 其他语言独立微服务集合。所有子目录是**独立可部署的服务**，与 `backend/`（Java）通过内部 HTTP API + `X-Internal-Service-Key` 通信，不共享 JVM/Maven 边界。

## 子目录

| 目录 | 用途 | 入口 | 内部 URL |
|------|------|------|---------|
| `alethicode-rag/` | RAG 检索与召回服务（基于 LightRAG / pgvector / memgraph） | `app/main.py` (FastAPI) | `http://alethicode-rag:8200` |
| `tutor-graph/` | AI 导学工作流（基于 LangGraph）；负责 7 个 phase 状态机 + Agent 编排 + LLM 调用 | `app/main.py` (FastAPI + LangGraph) | `http://tutor-graph:8100` |

## 调用约定

- 所有跨服务请求必须携带 header `X-Internal-Service-Key: <env.INTERNAL_SERVICE_KEY>`
- 服务侧由 `internal_key_validator` 强校验，非匹配立即 401
- backend → tutor-graph 通过 `TUTOR_GRAPH_BASE_URL` 环境变量寻址
- backend → alethicode-rag 通过 `RAG_SERVICE_URL` 寻址

## 部署

每个服务独立 Dockerfile：
- `services/tutor-graph/Dockerfile` → 镜像 `alethicode/tutor-graph`
- `services/alethicode-rag/Dockerfile` → 镜像 `alethicode/alethicode-rag`

Docker Compose 编排：`deploy/docker-compose.yml`  
Kubernetes：`deploy/k8s/*-deployment.yaml`、`deploy/helm/alethicode/templates/*.yaml`

## 新增服务

新加服务前请确认：
1. 与 backend 是真正的进程级独立（不能通过 import 调用）
2. 有独立的 Dockerfile + pyproject.toml / package.json
3. 与其他服务通过 HTTP API 通信，定义在 `contracts/<service>/`
4. 添加 `deploy/docker-compose.yml` 服务条目 + `deploy/helm/alethicode/templates/<service>-deployment.yaml`
