import _judger
import hashlib
import json
import logging
import os
import shutil
import shlex
from concurrent.futures import as_completed

from config import TEST_CASE_DIR, JUDGER_RUN_LOG_PATH, RUN_GROUP_GID, RUN_USER_UID, SPJ_EXE_DIR, SPJ_USER_UID, SPJ_GROUP_GID, RUN_GROUP_GID
from exception import JudgeClientError
from utils import ProblemIOMode
from worker_pool import (
    PRIORITY_FORMAL,
    QueueFull,
    VALID_PRIORITIES,
    get_worker_pool,
)

try:
    from diagnosis.engine import diagnose as _diagnose_case, empty_diagnosis as _empty_diagnosis
    _DIAGNOSIS_AVAILABLE = True
except Exception:
    _DIAGNOSIS_AVAILABLE = False

logger = logging.getLogger(__name__)

SPJ_WA = 1
SPJ_AC = 0
SPJ_ERROR = -1


def _normalize_test_case_id(case_id):
    try:
        return int(str(case_id))
    except (TypeError, ValueError):
        return 0


class JudgeClient(object):
    def __init__(self, run_config, exe_path, max_cpu_time, max_memory, test_case_dir,
                 submission_dir, spj_version, spj_config, io_mode, output=False,
                 language="", src=""):
        self._run_config = run_config
        self._exe_path = exe_path
        self._language = language
        self._src = src
        self._max_cpu_time = max_cpu_time
        self._max_memory = max_memory
        self._max_real_time = self._max_cpu_time * 3
        self._test_case_dir = test_case_dir
        self._submission_dir = submission_dir

        self._test_case_info = self._load_test_case_info()

        self._spj_version = spj_version
        self._spj_config = spj_config
        self._output = output
        self._io_mode = io_mode

        if self._spj_version and self._spj_config:
            self._spj_exe = os.path.join(SPJ_EXE_DIR,
                                         self._spj_config["exe_name"].format(spj_version=self._spj_version))
            if not os.path.exists(self._spj_exe):
                raise JudgeClientError("spj exe not found")

    def _load_test_case_info(self):
        try:
            with open(os.path.join(self._test_case_dir, "info")) as f:
                return json.load(f)
        except IOError:
            raise JudgeClientError("Test case not found")
        except ValueError:
            raise JudgeClientError("Bad test case config")

    def _get_test_case_file_info(self, test_case_file_id):
        return self._test_case_info["test_cases"][test_case_file_id]

    def _compare_output(self, test_case_file_id, user_output_file):
        with open(user_output_file, "rb") as f:
            content = f.read()
        output_md5 = hashlib.md5(content.rstrip()).hexdigest()
        result = output_md5 == self._get_test_case_file_info(test_case_file_id)["stripped_output_md5"]
        return output_md5, result

    def _spj(self, in_file_path, user_out_file_path):
        os.chown(self._submission_dir, SPJ_USER_UID, 0)
        os.chown(user_out_file_path, SPJ_USER_UID, 0)
        os.chmod(user_out_file_path, 0o740)
        command = self._spj_config["command"].format(exe_path=self._spj_exe,
                                                     in_file_path=in_file_path,
                                                     user_out_file_path=user_out_file_path)
        command = shlex.split(command)
        seccomp_rule_name = self._spj_config["seccomp_rule"]
        result = _judger.run(max_cpu_time=self._max_cpu_time * 3,
                             max_real_time=self._max_cpu_time * 9,
                             max_memory=self._max_memory * 3,
                             max_stack=128 * 1024 * 1024,
                             max_output_size=1024 * 1024 * 1024,
                             max_process_number=_judger.UNLIMITED,
                             exe_path=command[0],
                             input_path=in_file_path,
                             output_path="/tmp/spj.out",
                             error_path="/tmp/spj.out",
                             args=command[1::],
                             env=["PATH=" + os.environ.get("PATH", "")],
                             log_path=JUDGER_RUN_LOG_PATH,
                             seccomp_rule_name=seccomp_rule_name,
                             uid=SPJ_USER_UID,
                             gid=SPJ_GROUP_GID)

        if result["result"] == _judger.RESULT_SUCCESS or \
                (result["result"] == _judger.RESULT_RUNTIME_ERROR and
                 result["exit_code"] in [SPJ_WA, SPJ_ERROR] and result["signal"] == 0):
            return result["exit_code"]
        else:
            return SPJ_ERROR

    def _judge_one(self, test_case_file_id):
        test_case_info = self._get_test_case_file_info(test_case_file_id)
        in_file = os.path.join(self._test_case_dir, test_case_info["input_name"])

        if self._io_mode["io_mode"] == ProblemIOMode.file:
            user_output_dir = os.path.join(self._submission_dir, str(test_case_file_id))
            os.mkdir(user_output_dir)
            os.chown(user_output_dir, RUN_USER_UID, RUN_GROUP_GID)
            os.chmod(user_output_dir, 0o711)
            os.chdir(user_output_dir)
            # 文件输出模式依赖沙箱用户权限，后续若改目录权限需同步验证。
            user_output_file = os.path.join(user_output_dir, self._io_mode["output"])
            real_user_output_file = os.path.join(user_output_dir, "stdio.txt")
            shutil.copyfile(in_file, os.path.join(user_output_dir, self._io_mode["input"]))
            kwargs = {"input_path": in_file, "output_path": real_user_output_file, "error_path": real_user_output_file}
        else:
            real_user_output_file = user_output_file = os.path.join(self._submission_dir, test_case_file_id + ".out")
            kwargs = {"input_path": in_file, "output_path": real_user_output_file, "error_path": real_user_output_file}

        command = self._run_config["command"].format(exe_path=self._exe_path, exe_dir=os.path.dirname(self._exe_path),
                                                     max_memory=int(self._max_memory / 1024))
        command = shlex.split(command)
        env = ["PATH=" + os.environ.get("PATH", "")] + self._run_config.get("env", [])

        seccomp_rule = self._run_config["seccomp_rule"]
        if isinstance(seccomp_rule, dict):
            seccomp_rule = seccomp_rule[self._io_mode["io_mode"]]

        run_result = _judger.run(max_cpu_time=self._max_cpu_time,
                                 max_real_time=self._max_real_time,
                                 max_memory=self._max_memory,
                                 max_stack=128 * 1024 * 1024,
                                 max_output_size=max(test_case_info.get("output_size", 0) * 2, 1024 * 1024 * 16),
                                 max_process_number=_judger.UNLIMITED,
                                 exe_path=command[0],
                                 args=command[1::],
                                 env=env,
                                 log_path=JUDGER_RUN_LOG_PATH,
                                 seccomp_rule_name=seccomp_rule,
                                 uid=RUN_USER_UID,
                                 gid=RUN_GROUP_GID,
                                 memory_limit_check_only=self._run_config.get("memory_limit_check_only", 0),
                                 **kwargs)
        run_result["test_case"] = test_case_file_id

        # 程序正常退出后再比较输出结果。
        run_result["output_md5"] = None
        run_result["output"] = None
        if run_result["result"] == _judger.RESULT_SUCCESS:
            if not os.path.exists(user_output_file):
                run_result["result"] = _judger.RESULT_WRONG_ANSWER
            else:
                if self._test_case_info.get("spj"):
                    if not self._spj_config or not self._spj_version:
                        raise JudgeClientError("spj_config or spj_version not set")

                    spj_result = self._spj(in_file_path=in_file, user_out_file_path=user_output_file)

                    if spj_result == SPJ_WA:
                        run_result["result"] = _judger.RESULT_WRONG_ANSWER
                    elif spj_result == SPJ_ERROR:
                        run_result["result"] = _judger.RESULT_SYSTEM_ERROR
                        run_result["error"] = _judger.ERROR_SPJ_ERROR
                else:
                    run_result["output_md5"], is_ac = self._compare_output(test_case_file_id, user_output_file)
                    # -1 表示 Wrong Answer。
                    if not is_ac:
                        run_result["result"] = _judger.RESULT_WRONG_ANSWER

        if self._output:
            try:
                with open(user_output_file, "rb") as f:
                    run_result["output"] = f.read().decode("utf-8", errors="backslashreplace")
            except Exception:
                pass

        if _DIAGNOSIS_AVAILABLE:
            try:
                run_result["edu_diagnosis"] = _diagnose_case(
                    case_result=run_result,
                    language=self._language,
                    src=self._src,
                )
            except Exception:
                logger.exception("edu_diagnosis generation failed for case %s", test_case_file_id)
                run_result["edu_diagnosis"] = _empty_diagnosis()

        return run_result

    def run(self, priority=PRIORITY_FORMAL, on_case_done=None, rule_type="ACM"):
        """跑所有测试点。

        ``rule_type`` = ``"ACM"`` 时，首个非 AC 测试点完成后取消后续任务并
        返回；``rule_type`` = ``"OI"`` 时跑完所有测试点。

        返回的 ``data`` 列表按 ``test_case`` 数值升序排序，与上游一致。
        ACM 短路时额外附加 ``_early_stop`` 元字段到返回的 dict wrapper 中（由
        server 层读取并追加到响应 JSON）。
        """
        if priority not in VALID_PRIORITIES:
            raise JudgeClientError(
                "invalid priority: %s; must be one of %s" % (priority, list(VALID_PRIORITIES))
            )

        acm_mode = (rule_type or "ACM").upper() == "ACM"
        pool = get_worker_pool()
        futures = []
        case_ids = list(self._test_case_info["test_cases"].keys())
        try:
            for test_case_file_id in case_ids:
                future = pool.submit(priority, self._judge_one, test_case_file_id)
                futures.append(future)
        except QueueFull:
            for f in futures:
                f.cancel()
            raise

        result = []
        first_exception = None
        early_stop = None
        for future in as_completed(futures):
            try:
                case_result = future.result()
            except BaseException as exc:  # noqa: BLE001
                if first_exception is None:
                    first_exception = exc
                logger.exception("judge case raised exception: %s", exc)
                continue
            result.append(case_result)
            if on_case_done is not None:
                try:
                    on_case_done(case_result)
                except Exception:  # noqa: BLE001
                    logger.exception("on_case_done callback failed")

            if acm_mode and case_result.get("result", 0) != 0 and early_stop is None:
                early_stop = {
                    "stopped_at_case": case_result.get("test_case"),
                    "reason": "ACM_FIRST_FAIL",
                }
                for pending in futures:
                    pending.cancel()
                break

        if first_exception is not None:
            raise first_exception

        result.sort(key=lambda r: _normalize_test_case_id(r.get("test_case")))

        self._early_stop = early_stop
        return result
