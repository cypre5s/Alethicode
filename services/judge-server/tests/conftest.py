"""pytest 共享 fixtures。

Phase 0 host 烟测覆盖 ``judge_server`` 包元数据、工程化壳层文件齐全性、
配置文件解析三类纯 Python 行为。

Phase 1 起，host 上还要测 ``server.py`` 的 dispatch 分流逻辑（stream /
callback_url / sync 三模式 + 互斥校验）。``server.py`` 间接依赖：

- ``_judger``：C 扩展模块（host 没有，要 mock）
- ``pwd.getpwnam("compiler"/"code"/"spj")``：判题机镜像内的特殊用户（host
  没有，要 mock）
- ``logging.FileHandler("/log/judge_server.log")``：判题机镜像内挂载的目录
  （host 没权限写 ``/``，要把 FileHandler 退化成 NullHandler）

下面在 import 阶段一次性补齐这些 mock，让 host 能 import server.py 跑路由
契约测试；真实的 ``_judger.run`` 沙箱执行仍然需要在判题镜像里跑。
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path
from unittest.mock import MagicMock

ROOT = Path(__file__).resolve().parent.parent

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# 模拟 _judger（C 扩展），暴露上游 server / judge_client 用到的常量。
if "_judger" not in sys.modules:
    _judger_mock = MagicMock()
    _judger_mock.VERSION = 0x020101
    _judger_mock.UNLIMITED = -1
    _judger_mock.RESULT_SUCCESS = 0
    _judger_mock.RESULT_WRONG_ANSWER = -1
    _judger_mock.RESULT_CPU_TIME_LIMIT_EXCEEDED = 1
    _judger_mock.RESULT_REAL_TIME_LIMIT_EXCEEDED = 2
    _judger_mock.RESULT_MEMORY_LIMIT_EXCEEDED = 3
    _judger_mock.RESULT_RUNTIME_ERROR = 4
    _judger_mock.RESULT_SYSTEM_ERROR = 5
    _judger_mock.ERROR_SPJ_ERROR = -11
    sys.modules["_judger"] = _judger_mock

# 模拟 pwd.getpwnam / grp.getgrnam 对三个特殊用户的查询。
import pwd  # noqa: E402
import grp  # noqa: E402

_real_getpwnam = pwd.getpwnam
_real_getgrnam = grp.getgrnam

_FAKE_PWUIDS = {"compiler": 901, "code": 902, "spj": 903}


def _fake_getpwnam(name):
    if name in _FAKE_PWUIDS:
        ent = MagicMock()
        ent.pw_uid = _FAKE_PWUIDS[name]
        ent.pw_gid = _FAKE_PWUIDS[name]
        return ent
    return _real_getpwnam(name)


def _fake_getgrnam(name):
    if name in _FAKE_PWUIDS:
        ent = MagicMock()
        ent.gr_gid = _FAKE_PWUIDS[name]
        return ent
    return _real_getgrnam(name)


pwd.getpwnam = _fake_getpwnam
grp.getgrnam = _fake_getgrnam

# 模拟 logging.FileHandler 退化成 NullHandler，规避 /log/* 写权限。
_real_file_handler = logging.FileHandler


class _NullFileHandler(logging.NullHandler):
    def __init__(self, *args, **kwargs):
        super().__init__()


logging.FileHandler = _NullFileHandler


# 给 utils.py 提供有效 TOKEN 与 judger_debug=1，避免清理不存在的临时目录。
import os  # noqa: E402

os.environ.setdefault("TOKEN", "alethicode-host-test-token")
os.environ.setdefault("judger_debug", "1")

