"""Phase 0 烟测：判题机源码迁入项目后，工程化壳层必须在 host 上可用。

只覆盖纯 Python / 纯文件存在性的行为；真实判题逻辑（_judger.run + setuid +
seccomp）需要 Linux + libseccomp + libjudger.so + 三个特殊用户，host 上不
具备，请在判题镜像内单独跑。
"""

from __future__ import annotations

import importlib
import sys
from pathlib import Path

import pytest

if sys.version_info >= (3, 11):
    import tomllib
else:
    import tomli as tomllib

ROOT = Path(__file__).resolve().parent.parent


def test_judge_server_package_metadata():
    pkg = importlib.import_module("judge_server")
    assert pkg.__version__ == "0.0.1"


def test_judge_server_python_sources_present():
    """fork 自上游 server/ 的 8 个 Python 文件必须齐全。"""
    expected = {
        "server.py",
        "judge_client.py",
        "compiler.py",
        "config.py",
        "exception.py",
        "service.py",
        "utils.py",
        "unbuffer.c",
        "entrypoint.sh",
    }
    actual = {p.name for p in (ROOT / "judge_server").iterdir() if p.is_file()}
    missing = expected - actual
    assert not missing, f"missing upstream files in judge_server/: {sorted(missing)}"


def test_judger_c_kernel_sources_present():
    """fork 自上游 Judger/newnew 的 C 内核构建产物必须齐全。"""
    judger_root = ROOT / "judger"
    assert (judger_root / "CMakeLists.txt").is_file()
    assert (judger_root / "src").is_dir()
    assert (judger_root / "src" / "main.c").is_file()
    assert (judger_root / "src" / "runner.c").is_file()
    assert (judger_root / "src" / "rules").is_dir()
    assert (judger_root / "bindings" / "Python" / "_judger" / "__init__.py").is_file()


def test_default_config_toml_parses_and_lists_future_phases():
    """configs/default.toml 是 ops 唯一索引，未来每个 Phase 的开关都要在这里注册。"""
    config_path = ROOT / "configs" / "default.toml"
    raw = tomllib.loads(config_path.read_text(encoding="utf-8"))

    expected_sections = {"worker_pool", "diagnosis", "explain", "scheduling", "metrics", "trace", "safety"}
    missing = expected_sections - raw.keys()
    assert not missing, f"missing sections in default.toml: {sorted(missing)}"

    assert raw["worker_pool"]["priority_levels"] == ["formal", "debug", "trace"]
    assert raw["scheduling"]["default_rule_type"] in {"ACM", "OI"}
    assert raw["metrics"]["path"].startswith("/")


def test_engineering_shell_files_present():
    """工程化壳层文件齐全：构建 / 文档 / 配置 / 测试入口。"""
    expected = {
        "README.md",
        "Dockerfile",
        "Makefile",
        "pyproject.toml",
        ".gitignore",
        ".dockerignore",
        "configs/default.toml",
        "docs/UPSTREAM.md",
        "docs/release-notes.md",
    }
    for relative in expected:
        assert (ROOT / relative).exists(), f"missing engineering shell file: {relative}"


def test_pyproject_metadata_minimum_invariants():
    raw = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))
    project = raw["project"]
    assert project["name"] == "alethicode-judge-server"
    # 生产判题镜像内固定 python3.12，但 host 兼容到 3.10（依赖 tomli 兜底）。
    assert project["requires-python"].startswith(">=3.10")
    deps = set(project["dependencies"])
    assert any(d.startswith("flask") for d in deps)
    assert any(d.startswith("gunicorn") for d in deps)
    assert any(d.startswith("psutil") for d in deps)


@pytest.mark.sandbox
def test_judge_client_runs_in_sandbox():
    """真实判题需要在判题镜像里跑；host 上自动跳过。"""
    pytest.skip("requires libseccomp + libjudger.so + sandbox users")
