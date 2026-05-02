# M12 独立部署说明

## 目录
- `docker-compose.yml`：独立编排（PostgreSQL + Redis + Java Backend + **tutor-graph** + Judge + Frontend，可选开 monitoring / observability profile）
- `frontend.Dockerfile`：以 `frontend` 为默认前端构建并打包静态资源
- `frontend-nginx.conf`：统一入口，`/api` 与 `/ws` 代理到 Java 后端
- `k8s/`：裸 K8s manifest（PDB / HPA / Service / Deployment 完整版本），适合直接 `kubectl apply`
- `helm/alethicode/`：Helm chart，默认 values 对齐本地演示（单副本 + 内置 postgres/redis + tutor-graph），生产请用 overlay
- `loadtest/k6/`：3 个 k6 压测场景（见 `k6/README.md`）
- `chaos/`：ChaosBlade 演练清单
- `observability/`：Prometheus / Grafana / alerts
- `argocd/`：GitOps application 定义

## 一键启动
```bash
cd /home/cypress/Alethicode
bash scripts/m12/m12_up.sh
```

启动后访问：
- 前端入口：`http://127.0.0.1:18080`
- 后端调试端口：`http://127.0.0.1:8081`

## Smoke 验证
```bash
cd /home/cypress/Alethicode
bash scripts/m12/m12_smoke.sh
```

## 停止
```bash
cd /home/cypress/Alethicode
bash scripts/m12/m12_down.sh
```

## Ubuntu / WSL2 比赛安装包

如需提交“可执行文件或安装包”，可使用仓库新增的 `.run` 安装包构建链路：

```bash
cd /home/cypress/Alethicode
bash scripts/competition/build_competition_installer.sh
```

构建完成后产物位于：

- `release/competition_installer/Alethicode-Installer.run`

安装方式：

```bash
chmod +x release/competition_installer/Alethicode-Installer.run
./release/competition_installer/Alethicode-Installer.run
```

安装完成后进入安装目录执行：

```bash
./alethicode_competition/bin/start.sh
./alethicode_competition/bin/status.sh
./alethicode_competition/bin/smoke.sh
./alethicode_competition/bin/stop.sh
```

默认访问地址：

- `http://127.0.0.1:18080`

## 语言包初始化依赖

后端运行镜像已包含 Python3、LibreOffice 和必要 pip 库（pypdf、python-pptx、python-docx），用于语言包课件的格式规范化与页级解析。容器内 `/data/language_pack` 挂载到宿主机 `deploy/data/language_pack`。

如需本地开发环境运行规范化脚本，请确保安装：
```bash
sudo apt-get install -y python3 python3-pip libreoffice-core libreoffice-writer libreoffice-impress
pip3 install pypdf python-pptx python-docx
```
