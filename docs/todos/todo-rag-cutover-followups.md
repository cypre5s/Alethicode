# Sprint 12 RAG 切流后续待办

> **背景**：4/29 完成 sprint 12 RAG 切流落地修复（commit `f5e538bb` → `4ceff571`，5 个连续 commit）。alethicode-rag 微服务端到端可用，Python 语言基础（lp 43）KG 回填 in flight。本清单记录"切流闭环"之后立即与中长期需要做的事，按"价值 / 工时"排序，便于下个迭代点单。

---

## 当前已完成（前置）

- `tutor-graph/` 误删 73 个源文件从 git HEAD 恢复到 `services/tutor-graph/`
- `LanguagePackQaServiceImpl.java` 三处 SQL 残留 `p.page_embedding` 引用清掉（V77 后字段已 DROP）
- start.sh 加 `start_alethicode_rag` + 健康检查 + `RAG_SERVICE_URL` 强制覆盖
- `docker-compose.yml` alethicode-rag 切 `network_mode: host`（WSL2 mirrored networking 下唯一能透传 Windows 代理），memgraph 切 platform v2.14.1（v3.9 SIGSEGV）+ named volume（host bind mount SIGABRT）
- 反馈按钮从 fixed FAB 重构为 navbar 内嵌按钮 + tooltip + 中文 label
- `scripts/ops/rag_backfill.py` 加 `httpx trust_env=False` 绕代理 + `--language-pack-id` 单 pack 过滤
- LightRAG `llm_model_max_async` / `embedding_func_max_async` / `max_parallel_insert` 显式拉高，回填速率提升 ~3×
- Python 语言基础（lp 43, 561 page）已经 POST 入队，后台 KG 抽取持续推进（实测 18:22 nodes=1229 edges=1482，速率 ~4.2 doc/min，预计 19:25-19:30 跑完）

---

## A. 立即任务（建议优先做，最高 ROI）

### A.1 前端 UI 端到端验证 — ~30min
**价值**：确认本轮所有改动在浏览器里真的成立，避免代码修复但 UX 不通的盲点。

**步骤**：
- 启 Playwright headless（项目里已装 v1.30.0，chromium 也 cached）
- root/root123456 登录
- 路径 1：访问 `/language-pack-qa`，选 Python 语言基础进入对话，提问"什么是 for 循环"，截图响应。预期：能拿到来自 `language_pack/43/p*` 的真实课件 chunk + KG 实体（如「列表」「字典」），不再 502
- 路径 2：访问 `/problem`，登录 navbar 应有「反馈」按钮在最右侧（图标 + 中文文字 + tooltip），点击弹出反馈 dialog
- 路径 3：访问 `/problem/<某个题>`，确认 navbar 有反馈按钮、右下角有 AI 导学 FAB，两者**不重叠**
- 路径 4：开 DevTools Console 抓 navbar 区域的 `getBoundingClientRect()` + `svg path count`，确认所有 navbar 图标 visible 且有 svg 内容（解决之前用户提的"前面小图标没正确显示"）

**完成定义**：
- [ ] 4 条路径全部抓到截图
- [ ] 课件问答返回非空响应（含 entity 或 chunk）
- [ ] navbar 反馈按钮位置 + 样式符合预期
- [ ] navbar 内所有 ElIcon 都有 svg path > 0

### A.2 notebook + memory backfill — ~5min
**价值**：错题本(24) + 长期记忆(44) 共 68 行数据进 KG，是"AI 角色错误记忆系统"（Phase B）的最小数据基线。

**命令**：
```bash
cd /home/cypress/Alethicode
# 加载真实 DB_PASSWORD + RAG endpoint
set -a; source deploy/.env; source backend/.env; set +a
export DB_PASSWORD="$(docker inspect java-oj-postgres --format '{{range .Config.Env}}{{println .}}{{end}}' | awk -F= '$1=="POSTGRES_PASSWORD"{print substr($0, index($0,"=")+1); exit}')"
export POSTGRES_PASSWORD="$DB_PASSWORD"
export POSTGRES_HOST=127.0.0.1 POSTGRES_PORT=5436 POSTGRES_USER=onlinejudge POSTGRES_DATABASE=alethicode
export RAG_SERVICE_URL=http://127.0.0.1:8200 RAG_INTERNAL_TOKEN=dev-internal-key

python3 scripts/ops/rag_backfill.py --all --entity-types notebook memory --concurrency 4
```

**完成定义**：
- [ ] notebook total=24 finished=24 failed=0
- [ ] memory total=44 finished=44 failed=0
- [ ] memgraph nodes 增长（每条 notebook/memory 抽 2-5 entity）

### A.3 C 语言 + Python3-mini 也回填 — ~1h
**价值**：三大 pack 全部建 KG，平台所有课件问答都能用 KG 召回。  
**命令**：
```bash
# 在加载完 env 的同 shell 里
python3 scripts/ops/rag_backfill.py --all --language-pack-id 42 --entity-types courseware-page --concurrency 4
python3 scripts/ops/rag_backfill.py --all --language-pack-id 34 --entity-types courseware-page --concurrency 4
```

**完成定义**：
- [ ] lp 42 (C 语言基础, 189 page) finished=189 failed=0
- [ ] lp 34 (Python3-mini, 118 page) finished=118 failed=0
- [ ] memgraph 总 nodes 较 Python-only 阶段再 +500 量级

---

## B. 中期（半天 ~ 一天）

### B.1 sprint 12 切流回归测试套件
**价值**：确认 V77（DROP page_embedding 等）+ HttpRagServiceClient + RagIndexOutboxWorker 三件套在所有依赖路径下都不再回归到旧 SQL 字段或旧本地 embedding。

**任务**：
- 跑 `mvn test -Dtest='*RagService*Test,*RetrievalService*Test,*OutboxWorker*Test'`，绿
- 跑前端 `tests/unit/agent-card-kc-refs-contract.spec.js` 等契约测试，绿
- 用 Playwright 跑现有 e2e（`review-package-rating.spec.js` 之类），可能要修测试种子（前面 4/28 冒烟报告里提过）

### B.2 start.sh `resolve_postgres_credentials` ↔ `start_alethicode_rag` 链路自动化
**价值**：当前我们临时手动 `set -a; source ...; export DB_PASSWORD; docker compose up -d --no-deps alethicode-rag` 才能让密码注入正确。`start_alethicode_rag` 已经放在 resolve 之后，但 `docker compose up` 是否真把 shell 的 DB_PASSWORD 透传给 docker compose 替换还要在新 shell 里跑通一遍 cold-start 验证。

**任务**：
- 关掉所有相关进程 + 容器
- 直接 `bash start.sh` 一次跑，验证 alethicode-rag healthy 且 `/health` 返回 `postgres:ok memgraph:ok`，无需任何手动 export
- 如失败，把 `start_alethicode_rag` 函数内部加显式 `DB_PASSWORD="${DB_PASSWORD}" docker compose up -d ...`

### B.3 alethicode-rag 容器内 `~/.cache/tiktoken` 持久化
**价值**：当前每次容器 recreate 都要重新下载 tiktoken o200k_base BPE（3.6MB）。换成 named volume 或 build 期 ADD 进镜像可以省事。

---

## C. 长期（多天，对应 AGENTS.md 增强路线图）

> 路线图原文：Phase A 真实判题 + 学情同步；Phase B AI Agent 角色化 + 错误记忆；Phase C 课件融入 + 自适应难度。

### C.1 Phase B-1：AI Agent 角色化
- Nene 教学 → `problem_guide` Agent 生成审题引导
- Yoshino 纠错 → `error_diagnosis` Agent 分析具体错误
- Kanna 总结 → `post_ac` Agent 给出代码优化方向
- Murasame 进阶 → `transfer_problem` Agent 推荐迁移题
- 角色 LLM 对话的 system prompt 融入 Agent 返回的分析结果

### C.2 Phase B-2：错误记忆系统
- 接入 OJ 的 `misconception_tracking` 数据
- 角色 LLM system prompt 注入近期错误模式（基于 A.2 已经回填的 notebook + memory KG）
- Nene：「上次你在 for 循环的范围上写错了，记得 range(n) 是 0 到 n-1 哦～」
- Yoshino：「你的缩进问题……第三次了」

### C.3 Phase C-1：课件融入剧情
- 「桐生先生讲课」场景拉取 `language-pack` 中对应章节的 PPT 页面（A.3 完成后即可用）
- 课后角色总结引用课件知识点（复用 KG 检索 + `courseware_refs`）
- 可点击课件引用在游戏内嵌或新标签页查看

### C.4 Phase C-2：自适应难度
- 调用 `supplement-plan` API 根据学生薄弱 KC 推荐题目
- 失败次数多时自动降级到 `faded_example`（渐退示例）
- 角色教学节奏根据掌握度动态调整

---

## D. 已知潜在风险

- LightRAG `failed=928` 历史记录：之前 LLM auth 失败时 backfill 留下的死信。短期不阻塞 query（dedup 跳过），长期建议批量清理或 retry。
- memgraph `memgraph-platform:latest` 镜像较大（6.5GB）。等 memgraph 官方修复 v3.x 在 WSL2 6.6 上的 SIGSEGV 后切回轻量 `memgraph:latest`。
- alethicode-rag 必须 `network_mode: host`（WSL/dev only），如果迁到 Linux 服务器需要回到 docker bridge + 直接出网。

---

## E. 关于"YOLO 表情识别融入项目"

**结论：不推荐当前阶段融入**。详细分析：

| 维度 | 问题 |
| --- | --- |
| 法律 | 学生（含未成年）人脸生物特征属 PIPL 敏感个人信息，独立同意 + 数据脱敏 + 备案成本高 |
| 设备覆盖 | 机房旧机/平板/手机摄像头缺位、角度偏、光照差，检出率上限低 |
| 算法选型 | YOLO 是检测任务（bbox），表情识别是分类任务，应用 ViT/EfficientNet/AffectNet。"YOLO 表情"通常是 detect→crop→classify 两段 pipeline，单 YOLO 模型很难做到生产级精度 |
| 教学价值 | 平台核心痛点是「写错代码不会调试」「不会审题」，情绪识别对这两个痛点无直接干预 |
| 误检反噬 | 学生中性表情/低头打字/戴眼镜/光线暗 → 错判"沮丧"，触发不必要的安抚台词反而打断思考 |

### 强烈推荐替代方案：行为信号情绪推断

不用摄像头，用键盘 + 编辑器行为信号：

- **打字节奏**：停顿 / 退格频率 / 重写次数 → 焦虑指数
- **编辑器滞留**：在某行停留 > N 秒 → 困惑/卡住
- **提交频率**：5 分钟内 ≥ 3 次提交错误 → 受挫信号
- **调试次数**：debug 按钮点击次数 → 解题策略偏移

这些信号项目里**已经在采集**（`beta_telemetry_event` + `feature_click` + AI 错误诊断），无需新基础设施，无隐私风险，准确度更高（学生坐姿表情可以装，键盘节奏装不出来）。Phase B 路线图里"角色根据学生历史错误调整对话"实质就是这条路径。

### 万一坚持要做 YOLO

仅作为 **opt-in 实验功能**：
- 摄像头本地推理（不传服务器）
- 只输出情绪标签（不存原始图像）
- 独立隐私协议页
- 仅教学实验班级开放
- 必须能一键彻底关闭
