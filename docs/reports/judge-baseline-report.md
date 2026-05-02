# 判题机基线评估报告

> 生成日期: 2026-03-31
> 报告类型: todo_judge.md 第 2 阶段 - 基线冻结
> 评估范围: 当前 Alethicode 判题链路安全性与效率分析

---

## 1. 当前架构概述

### 1.1 判题链路
```
用户提交 → Java 后端 → 直连 JudgeServer (Python Flask) → Judger (C 沙箱)
                    ← 同步返回结果 ←
```

### 1.2 组件版本
| 组件 | 技术栈 | 版本 |
|------|--------|------|
| 业务后端 | Spring Boot | 3.4.4 |
| 判题服务 | Python Flask + gunicorn | Alethicode upstream |
| 沙箱内核 | C + seccomp + rlimits | Judger 2.1.1 |
| 数据库 | PostgreSQL | 最新 |

---

## 2. 安全性分析

### 2.1 现有安全机制

| 机制 | 实现状态 | 评估 |
|------|----------|------|
| Token 认证 | ✅ SHA256(token) 头部校验 | 静态 token，无过期机制 |
| seccomp 沙箱 | ✅ Judger C 内核 | 按语言配置 seccomp 规则 |
| rlimits 限制 | ✅ CPU/内存/进程数限制 | 由 Judger 设置 |
| UID 隔离 | ✅ 运行用户非 root | RUN_USER_UID / RUN_GROUP_GID |
| 工作目录隔离 | ✅ 每次提交独立目录 | 完成后 shutil.rmtree 清理 |
| 输出限制 | ✅ max_output_size | 防止输出爆量 |

### 2.2 安全薄弱点

| 风险 | 严重程度 | 说明 |
|------|----------|------|
| 静态 Token | 🟡 中 | Token 不过期、不轮换，一旦泄露无法撤销 |
| 无节点注册机制 | 🔴 高 | 任何知道 Token 的服务都能伪装为判题节点 |
| 无请求重放防护 | 🟡 中 | 无 nonce/timestamp 校验，heartbeat 可重放 |
| 后端直连执行节点 | 🔴 高 | 业务后端通过 service_url 直接调用判题机 HTTP 接口 |
| 明文 HTTP 通信 | 🟡 中 | 判题数据在网络中以明文传输 |
| 进程池竞争 | 🟡 中 | 每个判题请求新建 Pool(cpu_count())，高并发下进程数爆炸 |
| 清理失败无告警 | 🟡 中 | shutil.rmtree 失败仅写日志，无主动告警 |
| 无审计日志 | 🔴 高 | 认证失败、异常请求无结构化审计记录 |

### 2.3 seccomp 覆盖分析

| 语言 | seccomp 规则 | 覆盖评估 |
|------|-------------|----------|
| C/C++ | c_cpp | ✅ 严格限制 syscall |
| C/C++ File IO | c_cpp_file_io | ✅ 允许文件 IO |
| 通用规则 | general | ✅ 适中限制 |
| Go | golang | ✅ 允许 Go runtime syscalls |
| Node | node | ✅ 允许 V8 syscalls |
| Python | 无 seccomp | ⚠️ Python 不使用 seccomp，依赖 rlimits |
| Java | 无 seccomp | ⚠️ JVM 不使用 seccomp，依赖 rlimits |

---

## 3. 效率分析

### 3.1 当前判题流程

```
1. Java 后端选择节点（按 task_number 排序）
2. Java 后端直接 HTTP POST 到 JudgeServer
3. JudgeServer 创建工作目录
4. JudgeServer 写入源码文件
5. Compiler 编译（如需要）
6. JudgeClient.run() 创建 Pool(cpu_count())
7. 并行执行所有测试点
8. 收集结果
9. 清理工作目录
10. HTTP 返回结果
```

### 3.2 性能瓶颈分析

| 瓶颈 | 类型 | 影响 | 严重程度 |
|------|------|------|----------|
| 同步 HTTP 判题 | 架构 | Java 线程阻塞等待判题完成 | 🔴 高 |
| Pool(cpu_count()) | 资源 | 每次判题新建进程池，高并发时进程数爆炸 | 🔴 高 |
| 无队列机制 | 架构 | 无法控制并发、无法重试 | 🔴 高 |
| task_number 调度 | 算法 | 仅按任务数分配，不考虑 CPU/内存/队列状态 | 🟡 中 |
| 无租约机制 | 可靠性 | 节点挂掉后任务卡死 | 🔴 高 |
| 无 ACM 短路 | 效率 | WA 后仍运行所有测试点 | 🟡 中 |
| Flask 单进程模型 | 吞吐 | gunicorn -w 4 限制并发 | 🟡 中 |

### 3.3 预估性能指标（基于架构分析）

| 指标 | 预估值 | 说明 |
|------|--------|------|
| 单节点并发判题数 | cpu_count() | 受 Pool 限制 |
| 单次判题延迟 (Python) | 2-5s | 编译 + 运行 + 清理 |
| 单次判题延迟 (C/C++) | 1-3s | 编译 + 运行 + 清理 |
| 排队延迟 | 无限制 | 无队列机制，直接 HTTP 阻塞 |
| 吞吐上限 (4核节点) | ~240 tasks/min | 4 并发 × ~1s/task × 60s |

---

## 4. 改造建议优先级

### 4.1 安全改造（按优先级）

1. **P0**: 引入控制面，消除后端直连执行节点
2. **P0**: 节点注册与短期凭证机制
3. **P1**: 请求签名与重放防护
4. **P1**: 结构化审计日志
5. **P2**: Token 轮换机制
6. **P2**: Python/Java 的 seccomp 或 Landlock 加固

### 4.2 效率改造（按优先级）

1. **P0**: 异步队列替代同步 HTTP 判题
2. **P0**: 租约机制保证任务可靠性
3. **P1**: 基于多维指标的智能调度
4. **P1**: ACM 首错短路
5. **P2**: 复用进程池替代每请求新建
6. **P2**: 预热编译环境

---

## 5. 结论

当前 Alethicode 判题系统的核心问题是**架构层面的**：
- **安全性**：缺乏控制面隔离、节点认证机制和审计能力
- **效率**：同步 HTTP + 无队列 + 无租约的模式限制了吞吐和可靠性
- **可观测性**：todo_check_judge.md 已补充了监控基建

todo_judge.md 提出的 `judge-control` Go 服务方案是正确的架构方向，通过引入专用控制面来同时解决安全性、效率和可靠性问题。
