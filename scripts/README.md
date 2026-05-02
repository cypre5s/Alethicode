# scripts/ 目录索引

本目录按"执行域"分子目录组织，每个子目录独立维护。新增脚本前请先归类。

| 子目录 | 用途 | 包含脚本 |
|--------|------|---------|
| `backup/` | 备份与监控（数据库快照、Prometheus baseline） | `auto_backup.sh`, `backup_alethicode.sh`, `monitor_sql_baseline.sh`, `run_monitor_200_baseline.sh` |
| `deploy/` | 部署运维（Certbot、ECS、Crontab、APT 镜像） | `certbot_init.sh`, `certbot_renew.sh`, `configure_mirrors_by_ip.sh`, `deploy_env_to_ecs.sh`, `ecs_setup.sh`, `install_crontab.sh` |
| `m12/` | M12 契约保护与 smoke（启动契约、API baseline 守卫） | `check_m1_contract.sh`, `check_start_contract.sh`, `extract_source_api_baseline.sh`, `guard_no_api_v1.sh`, `m12_down.sh`, `m12_smoke.sh`, `m12_sync_frontend.sh`, `m12_up.sh`, `verify_alethicode_readonly.sh` |
| `competition/` | 比赛打包（一键安装包构建与测试） | `build_competition_installer.sh`, `test_competition_installer.sh` |
| `modeling/` | UML 建模脚本（用例图 / 活动图 / StarUML mdj） | `build_activity_diagrams.py`, `build_staruml_mdj.py`, `build_use_case_zh.py` |
| `seed/` | 数据种子（AI 演示数据、实验题目、KC 标签同步） | `seed_ai_showcase.sh`, `seed_lab_demo_problems.sh`, `sync_alethicode_tag_kc.py` |
| `ops/` | 运维杂项（RAG 回填与回归、SBOM、孤立语言包清理） | `cleanup_orphan_language_pack_dirs.sh`, `generate_sbom.sh`, `rag_backfill.py`, `rag_quality_regression.py`, `rag_regression_queries.json` |

## 调用约定

- 所有 `.sh` 通过 `cd "$(dirname "$0")/../.." && pwd` 计算 repo 根，
  这样 `scripts/<category>/foo.sh` 在任何工作目录下执行结果一致。
- 所有 `.py` 通过 `Path(__file__).resolve().parents[2]` 计算 repo 根。

## 历史脚本搬迁说明

2026-04-29 之前 `scripts/` 是扁平结构，本次重构后所有脚本归入子目录。
若 CI / 文档中仍有旧路径残留请提 PR 修复，或参考本次 commit 的全量替换列表。
