"""
重构 scripts/ 目录布局（一次性脚本，执行后可删除）。

把扁平的 32 个脚本按"执行域"归到 7 个子目录，并：
  · git mv 移动文件（保留历史）
  · 修每个 .sh 内部 ROOT 计算：'(dirname "$0")/..' → '(dirname "$0")/../..'
  · 修 Python 脚本：Path(__file__).resolve().parents[1] → parents[2]
  · 全局替换 docs/ deploy/ backend/ CHANGELOG.md / .github/ 等中的引用路径
  · 写入 scripts/README.md 索引

执行：
  python3 scripts/_refactor_scripts_layout.py
执行完检查 git status，若 OK 删除本脚本。
"""

import re
import subprocess
import sys
from pathlib import Path
from typing import Iterable

REPO = Path(__file__).resolve().parent.parent
SCRIPTS = REPO / "scripts"

# 文件 → 子目录
MAPPING: dict[str, str] = {
    # backup 备份与监控
    "auto_backup.sh": "backup",
    "backup_alethicode.sh": "backup",
    "monitor_sql_baseline.sh": "backup",
    "run_monitor_200_baseline.sh": "backup",
    # deploy 部署运维
    "certbot_init.sh": "deploy",
    "certbot_renew.sh": "deploy",
    "configure_mirrors_by_ip.sh": "deploy",
    "deploy_env_to_ecs.sh": "deploy",
    "ecs_setup.sh": "deploy",
    "install_crontab.sh": "deploy",
    # m12 契约保护与 smoke
    "m12_up.sh": "m12",
    "m12_down.sh": "m12",
    "m12_smoke.sh": "m12",
    "m12_sync_frontend.sh": "m12",
    "check_m1_contract.sh": "m12",
    "check_start_contract.sh": "m12",
    "extract_source_api_baseline.sh": "m12",
    "guard_no_api_v1.sh": "m12",
    "verify_alethicode_readonly.sh": "m12",
    # competition 比赛打包
    "build_competition_installer.sh": "competition",
    "test_competition_installer.sh": "competition",
    # modeling UML 建模脚本
    "build_use_case_zh.py": "modeling",
    "build_activity_diagrams.py": "modeling",
    "build_staruml_mdj.py": "modeling",
    # seed 数据种子
    "seed_ai_showcase.sh": "seed",
    "seed_lab_demo_problems.sh": "seed",
    "sync_alethicode_tag_kc.py": "seed",
    # ops 杂项运维
    "rag_backfill.py": "ops",
    "rag_quality_regression.py": "ops",
    "rag_regression_queries.json": "ops",
    "cleanup_orphan_language_pack_dirs.sh": "ops",
    "generate_sbom.sh": "ops",
}

CATEGORY_DESC = {
    "backup": "备份与监控（数据库快照、Prometheus baseline）",
    "deploy": "部署运维（Certbot、ECS、Crontab、APT 镜像）",
    "m12": "M12 契约保护与 smoke（启动契约、API baseline 守卫）",
    "competition": "比赛打包（一键安装包构建与测试）",
    "modeling": "UML 建模脚本（用例图 / 活动图 / StarUML mdj）",
    "seed": "数据种子（AI 演示数据、实验题目、KC 标签同步）",
    "ops": "运维杂项（RAG 回填与回归、SBOM、孤立语言包清理）",
}


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess:
    print(f"$ {' '.join(cmd)}")
    return subprocess.run(cmd, check=check, cwd=REPO, capture_output=True, text=True)


def step1_create_dirs() -> None:
    for cat in CATEGORY_DESC:
        d = SCRIPTS / cat
        d.mkdir(exist_ok=True)


def _is_tracked(rel_path: str) -> bool:
    """判断文件是否已被 git 跟踪。"""
    proc = subprocess.run(
        ["git", "ls-files", "--error-unmatch", rel_path],
        cwd=REPO, capture_output=True, text=True,
    )
    return proc.returncode == 0


def step2_git_mv() -> None:
    import shutil as _shutil
    for filename, cat in MAPPING.items():
        src = SCRIPTS / filename
        dst = SCRIPTS / cat / filename
        if not src.exists():
            print(f"[skip] 源文件不存在: {src}")
            continue
        if dst.exists():
            print(f"[skip] 目标已存在: {dst}")
            continue
        rel_src = str(src.relative_to(REPO))
        rel_dst = str(dst.relative_to(REPO))
        if _is_tracked(rel_src):
            run(["git", "mv", rel_src, rel_dst])
        else:
            print(f"$ mv (untracked) {rel_src} {rel_dst}")
            _shutil.move(str(src), str(dst))


def step3_fix_shell_root() -> None:
    """修每个移到子目录的 .sh：(dirname "$0")/.. → (dirname "$0")/../.."""
    for filename, cat in MAPPING.items():
        if not filename.endswith(".sh"):
            continue
        target = SCRIPTS / cat / filename
        if not target.exists():
            continue
        text = target.read_text(encoding="utf-8")
        new_text = text
        # 关键替换：精确匹配 dirname-pattern，再加一层 ..
        new_text = re.sub(
            r'\$\(cd "\$\(dirname "\$0"\)/\.\." && pwd\)',
            r'$(cd "$(dirname "$0")/../.." && pwd)',
            new_text,
        )
        new_text = re.sub(
            r'\$\(dirname "\$0"\)/\.\.([/"])',
            r'$(dirname "$0")/../..\1',
            new_text,
        )
        # BASH_SOURCE 形态
        new_text = re.sub(
            r'\$\(dirname "\$\{BASH_SOURCE\[0\]\}"\)/\.\.([/"])',
            r'$(dirname "${BASH_SOURCE[0]}")/../..\1',
            new_text,
        )
        if new_text != text:
            target.write_text(new_text, encoding="utf-8")
            print(f"[shell-root] {target.relative_to(REPO)}")


def step4_fix_python_root() -> None:
    """修 Python 脚本：parents[1] → parents[2]"""
    for filename, cat in MAPPING.items():
        if not filename.endswith(".py"):
            continue
        target = SCRIPTS / cat / filename
        if not target.exists():
            continue
        text = target.read_text(encoding="utf-8")
        new_text = re.sub(
            r"Path\(__file__\)\.resolve\(\)\.parents\[1\]",
            "Path(__file__).resolve().parents[2]",
            text,
        )
        if new_text != text:
            target.write_text(new_text, encoding="utf-8")
            print(f"[python-root] {target.relative_to(REPO)}")


def step5_replace_references() -> None:
    """全局替换文档/CI/部署中的引用路径。"""
    exclude_parts = {
        ".git", "node_modules", "__pycache__", ".pytest_cache",
        "scripts",  # 脚本内部已在 step3/4 处理；自引用单独搞
        "data",     # deploy/data/ 是 docker 数据卷，无读权限
        "target",   # Maven 构建产物
        "dist", "build",
    }

    targets: list[Path] = []
    for root in [".github", "backend", "deploy", "docs", "frontend", "tutor_graph"]:
        rd = REPO / root
        if not rd.exists():
            continue
        for path in rd.rglob("*"):
            if not path.is_file():
                continue
            if any(part in exclude_parts for part in path.parts):
                continue
            if path.suffix in (".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf",
                                ".lock", ".jar", ".class", ".zip", ".tar", ".gz",
                                ".woff", ".woff2", ".ttf", ".otf", ".eot",
                                ".ico", ".svg", ".mp4", ".mp3", ".rdb"):
                continue
            targets.append(path)

    targets.append(REPO / "CHANGELOG.md")
    targets.append(REPO / "PROJECT.md")
    targets.append(REPO / "TODO.md")
    targets.append(REPO / "AGENTS.md")
    targets.append(REPO / "README.md")
    targets.append(REPO / "BUSINESS_OPTIMIZATION_REPORT.md")
    targets.append(REPO / "PRODUCT_POLISH_CHECKLIST.md")
    targets.append(REPO / ".pre-commit-config.yaml")
    # 同步处理 nfk/ 内部的 README/EXPERIMENT_LOG 也可能引用 scripts/
    for f in (REPO / "nfk").rglob("*"):
        if f.is_file() and f.suffix in (".md", ".sh", ".py"):
            targets.append(f)

    seen = set()
    unique_targets = []
    for t in targets:
        if t in seen or not t.exists():
            continue
        seen.add(t)
        unique_targets.append(t)

    for fpath in unique_targets:
        try:
            text = fpath.read_text(encoding="utf-8")
        except (UnicodeDecodeError, PermissionError, OSError):
            continue
        new_text = text
        for filename, cat in MAPPING.items():
            old1 = f"scripts/{filename}"
            new1 = f"scripts/{cat}/{filename}"
            new_text = new_text.replace(old1, new1)
        if new_text != text:
            try:
                fpath.write_text(new_text, encoding="utf-8")
            except (PermissionError, OSError):
                print(f"[skip-write] {fpath.relative_to(REPO)}")
                continue
            print(f"[ref] {fpath.relative_to(REPO)}")


def step6_write_readme() -> None:
    lines = [
        "# scripts/ 目录索引",
        "",
        '本目录按"执行域"分子目录组织，每个子目录独立维护。新增脚本前请先归类。',
        "",
        "| 子目录 | 用途 | 包含脚本 |",
        "|--------|------|---------|",
    ]
    by_cat: dict[str, list[str]] = {}
    for filename, cat in MAPPING.items():
        by_cat.setdefault(cat, []).append(filename)
    for cat, desc in CATEGORY_DESC.items():
        files = sorted(by_cat.get(cat, []))
        lines.append(f"| `{cat}/` | {desc} | {', '.join(f'`{f}`' for f in files)} |")
    lines += [
        "",
        "## 调用约定",
        "",
        "- 所有 `.sh` 通过 `cd \"$(dirname \"$0\")/../..\" && pwd` 计算 repo 根，",
        "  这样 `scripts/<category>/foo.sh` 在任何工作目录下执行结果一致。",
        "- 所有 `.py` 通过 `Path(__file__).resolve().parents[2]` 计算 repo 根。",
        "",
        "## 历史脚本搬迁说明",
        "",
        "2026-04-29 之前 `scripts/` 是扁平结构，本次重构后所有脚本归入子目录。",
        "若 CI / 文档中仍有旧路径残留请提 PR 修复，或参考本次 commit 的全量替换列表。",
        "",
    ]
    (SCRIPTS / "README.md").write_text("\n".join(lines), encoding="utf-8")
    print("[readme] scripts/README.md")


def main() -> None:
    print(">>> Step 1: 创建子目录")
    step1_create_dirs()
    print(">>> Step 2: git mv 文件")
    step2_git_mv()
    print(">>> Step 3: 修 .sh 内部 ROOT 路径")
    step3_fix_shell_root()
    print(">>> Step 4: 修 .py 内部 ROOT 路径")
    step4_fix_python_root()
    print(">>> Step 5: 全局替换引用方")
    step5_replace_references()
    print(">>> Step 6: 写 scripts/README.md")
    step6_write_readme()
    print(">>> 完成。请运行 git status / git diff 验证。")


if __name__ == "__main__":
    main()
