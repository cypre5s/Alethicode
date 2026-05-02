# 云端数据迁移指南

> 将本地开发环境的数据库和课件文件迁移到云端 ECS 服务器。

---

## 迁移文件清单

| 文件 | 大小 | 内容 | 位置 |
|---|---|---|---|
| `alethicode_dump.sql.gz` | 1.9M | 全量数据库（用户/题目/提交/课程包/学情数据） | `deploy/data/` |
| `language_pack_full.tar.gz` | 1.2G | 课件原始文件（PPT/PDF + 标准化版本 + 预览） | `deploy/data/` |

---

## 第一步：定位云端部署目录

SSH 登录 ECS 后执行：

```bash
# 方法 1：通过 PostgreSQL 容器的挂载目录推断
DEPLOY_DIR=$(docker inspect java-oj-postgres \
  --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Source}}{{end}}{{end}}' \
  | sed 's|/data/postgres||')
echo "Deploy directory: $DEPLOY_DIR"

# 方法 2：直接搜索 docker-compose.yml
find / -name 'docker-compose.yml' -path '*/deploy/*' 2>/dev/null

# 方法 3：查看 backend 容器挂载
docker inspect java-oj-backend \
  --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'
```

确认 `$DEPLOY_DIR` 后面所有命令都基于这个路径。

---

## 第二步：上传文件到 ECS

从本地执行（替换 `YOUR_ECS_IP` 和 `YOUR_KEY`）：

```bash
# 上传数据库 dump
scp -i YOUR_KEY deploy/data/alethicode_dump.sql.gz root@YOUR_ECS_IP:/tmp/

# 上传课件文件（1.2G，可能需要几分钟）
scp -i YOUR_KEY deploy/data/language_pack_full.tar.gz root@YOUR_ECS_IP:/tmp/
```

---

## 第三步：恢复数据库

SSH 登录 ECS 后执行：

```bash
cd /tmp

# 1. 解压 SQL
gunzip alethicode_dump.sql.gz

# 2. 确保 pgvector 扩展已安装
docker exec java-oj-postgres psql -U onlinejudge -d alethicode \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 3. 复制到容器内
docker cp alethicode_dump.sql java-oj-postgres:/tmp/

# 4. 恢复数据（会覆盖现有数据）
docker exec java-oj-postgres psql -U onlinejudge -d alethicode \
  -f /tmp/alethicode_dump.sql

# 5. 验证关键表
docker exec java-oj-postgres psql -U onlinejudge -d alethicode -c "
SELECT 'user' as tbl, count(*) FROM \"user\"
UNION ALL SELECT 'problem', count(*) FROM problem
UNION ALL SELECT 'submission', count(*) FROM submission
UNION ALL SELECT 'language_pack', count(*) FROM language_pack
UNION ALL SELECT 'language_pack_page', count(*) FROM language_pack_page
UNION ALL SELECT 'ai_knowledge_component', count(*) FROM ai_knowledge_component
ORDER BY tbl;
"
```

预期输出：
```
 tbl                  | count
----------------------+-------
 ai_knowledge_component |   166
 language_pack          |     4
 language_pack_page     |  1429
 problem                |   110
 submission             |   249
 user                   |    28
```

---

## 第四步：恢复课件文件

```bash
# 1. 找到 deploy/data 目录
cd $DEPLOY_DIR/data

# 2. 如果已有旧的 language_pack 目录，先备份
mv language_pack language_pack_backup 2>/dev/null

# 3. 解压（会创建 language_pack/ 子目录）
tar xzf /tmp/language_pack_full.tar.gz

# 4. 确认文件结构
ls -la language_pack/
# 应该看到：preview/  tasks/

ls -la language_pack/tasks/
# 应该看到：33/  41/  42/  47/

# 5. 确认权限（确保 backend 容器能读取）
chmod -R 755 language_pack/
```

---

## 第五步：重启并验证

```bash
# 重启 backend 让新数据生效
cd $DEPLOY_DIR
docker compose restart backend

# 等待 backend 启动（约 15-30 秒）
sleep 20

# 验证 backend 健康
curl -s http://localhost:8080/api/health || curl -s http://localhost:8081/actuator/health

# 验证课件预览功能
# 在浏览器访问任意课件页面确认 PDF 预览正常
```

---

## 清理临时文件

```bash
rm -f /tmp/alethicode_dump.sql /tmp/alethicode_dump.sql.gz
rm -f /tmp/language_pack_full.tar.gz
docker exec java-oj-postgres rm -f /tmp/alethicode_dump.sql
```

---

## 故障排查

### 数据库恢复报错 "role onlinejudge does not exist"

```bash
# 查看实际的数据库用户
docker exec java-oj-postgres psql -U postgres -c "\du"
# 将命令中的 -U onlinejudge 替换为实际用户名
```

### 数据库恢复报错 "extension vector does not exist"

```bash
# 确认使用的是 pgvector 镜像
docker exec java-oj-postgres psql -U onlinejudge -d alethicode \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 课件预览页面 404

```bash
# 检查文件路径是否正确
docker exec java-oj-backend ls -la /data/language_pack/
# 如果是空的，检查 docker-compose.yml 中的挂载路径
docker inspect java-oj-backend --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'
```

### backend 启动失败

```bash
# 查看日志
docker logs java-oj-backend --tail 50
# 常见原因：数据库连接失败（需要等 postgres 完全启动）
docker compose restart backend
```

---

## 数据库内容说明

| 表 | 行数 | 说明 |
|---|---|---|
| `user` | 28 | 用户账号 |
| `problem` | 110 | OJ 题目 |
| `submission` | 249 | 代码提交记录 |
| `language_pack` | 4 | 课程包（Python3-mini / C语言基础 / Python语言基础 x2） |
| `language_pack_page` | 1,429 | 课件页面（PDF 解析后的逐页内容） |
| `language_pack_document` | 27 | 原始文档记录 |
| `ai_knowledge_component` | 166 | 知识点 |
| `language_pack_example` | 147 | 课件示例 |
| `ai_problem_kc_mapping` | 72 | 题目-知识点映射 |
| `ai_courseware_chunk` | 45 | 课件向量索引 |
| `ai_learner_memory` | 41 | 学习者记忆（含衰减） |
| `ai_workflow_session` | 37 | AI 导学会话 |
| `classroom` | 2 | 课堂 |
