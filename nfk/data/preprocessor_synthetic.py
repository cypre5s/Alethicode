"""
Python 教学 OJ 合成数据生成器。

生成符合 Alethicode KC 体系的学生交互数据，用于：
  - 当无真实数据时验证 NFK pipeline 端到端可用
  - 竞赛演示时展示模型在 Python 教学场景的效果
  - 模型开发阶段的快速迭代

模拟机制：
  - IRT 2PL 模型控制做题正确率
  - BKT 学习增长：每次正确练习提升该 KC 掌握度
  - Ebbinghaus 遗忘：间隔时间越长，掌握度衰减越多
  - 技能先修关系：list 依赖 for，dict 依赖 list 等
"""

from __future__ import annotations

from typing import Any

import numpy as np
from sklearn.model_selection import KFold

PYTHON_KCS = [
    {"id": 1, "name": "变量与赋值", "chapter": "基础", "difficulty": 0.2},
    {"id": 2, "name": "数据类型转换", "chapter": "基础", "difficulty": 0.3},
    {"id": 3, "name": "算术运算符", "chapter": "基础", "difficulty": 0.25},
    {"id": 4, "name": "字符串基础", "chapter": "基础", "difficulty": 0.3},
    {"id": 5, "name": "输入输出", "chapter": "基础", "difficulty": 0.2},
    {"id": 6, "name": "if-else 条件", "chapter": "控制流", "difficulty": 0.35},
    {"id": 7, "name": "比较与逻辑运算", "chapter": "控制流", "difficulty": 0.4},
    {"id": 8, "name": "for 循环基础", "chapter": "循环", "difficulty": 0.45},
    {"id": 9, "name": "range() 用法", "chapter": "循环", "difficulty": 0.5},
    {"id": 10, "name": "while 循环", "chapter": "循环", "difficulty": 0.5},
    {"id": 11, "name": "循环嵌套", "chapter": "循环", "difficulty": 0.65},
    {"id": 12, "name": "break 与 continue", "chapter": "循环", "difficulty": 0.55},
    {"id": 13, "name": "列表创建与索引", "chapter": "数据结构", "difficulty": 0.45},
    {"id": 14, "name": "列表切片", "chapter": "数据结构", "difficulty": 0.55},
    {"id": 15, "name": "列表方法", "chapter": "数据结构", "difficulty": 0.5},
    {"id": 16, "name": "列表推导式", "chapter": "数据结构", "difficulty": 0.65},
    {"id": 17, "name": "字符串方法", "chapter": "字符串", "difficulty": 0.5},
    {"id": 18, "name": "字符串格式化", "chapter": "字符串", "difficulty": 0.55},
    {"id": 19, "name": "函数定义与调用", "chapter": "函数", "difficulty": 0.55},
    {"id": 20, "name": "函数参数", "chapter": "函数", "difficulty": 0.6},
    {"id": 21, "name": "返回值", "chapter": "函数", "difficulty": 0.55},
    {"id": 22, "name": "变量作用域", "chapter": "函数", "difficulty": 0.7},
    {"id": 23, "name": "字典操作", "chapter": "数据结构", "difficulty": 0.6},
    {"id": 24, "name": "元组与集合", "chapter": "数据结构", "difficulty": 0.55},
    {"id": 25, "name": "文件读写", "chapter": "进阶", "difficulty": 0.65},
    {"id": 26, "name": "异常处理", "chapter": "进阶", "difficulty": 0.7},
    {"id": 27, "name": "递归", "chapter": "进阶", "difficulty": 0.8},
    {"id": 28, "name": "排序算法", "chapter": "进阶", "difficulty": 0.75},
]

PROBLEMS_PER_KC = 8


class SyntheticPythonPreprocessor:
    """
    生成 Python 教学场景的合成学生交互数据。

    Args:
        n_students: 学生数量
        min_interactions: 每个学生最少交互次数
        max_interactions: 每个学生最多交互次数
        seed: 随机种子
    """

    def __init__(
        self,
        n_students: int = 800,
        min_interactions: int = 30,
        max_interactions: int = 200,
        seed: int = 42,
    ):
        self.n_students = n_students
        self.min_interactions = min_interactions
        self.max_interactions = max_interactions
        self.seed = seed

        self.kcs = PYTHON_KCS
        self.n_kcs = len(self.kcs)
        self.n_problems = self.n_kcs * PROBLEMS_PER_KC
        self.question_map = {i + 1: i + 1 for i in range(self.n_problems)}
        self.skill_map = {kc["id"]: kc["id"] for kc in self.kcs}

    def load_and_preprocess(self) -> list[dict]:
        rng = np.random.RandomState(self.seed)

        kc_difficulties = np.array([kc["difficulty"] for kc in self.kcs])
        problem_difficulties = np.repeat(kc_difficulties, PROBLEMS_PER_KC)
        problem_difficulties += rng.normal(0, 0.05, self.n_problems)
        problem_difficulties = np.clip(problem_difficulties, 0.1, 0.95)

        problem_to_kc = np.repeat(np.arange(1, self.n_kcs + 1), PROBLEMS_PER_KC)

        sequences = []
        for uid in range(self.n_students):
            n_interactions = rng.randint(self.min_interactions, self.max_interactions + 1)
            ability = rng.normal(0.5, 0.2)
            mastery = np.full(self.n_kcs, 0.3)

            question_ids = []
            skill_ids = []
            responses = []
            timestamps = []

            base_time = 1704067200 + uid * 86400
            current_time = base_time

            for step in range(n_interactions):
                available_kcs = self._get_available_kcs(mastery)
                kc_idx = rng.choice(available_kcs)
                kc_problems = np.arange(kc_idx * PROBLEMS_PER_KC, (kc_idx + 1) * PROBLEMS_PER_KC)
                problem_idx = rng.choice(kc_problems)

                prob_correct = self._compute_prob_correct(
                    ability, mastery[kc_idx], problem_difficulties[problem_idx]
                )
                correct = int(rng.random() < prob_correct)

                question_ids.append(problem_idx + 1)
                skill_ids.append(kc_idx + 1)
                responses.append(correct)
                timestamps.append(float(current_time))

                if correct:
                    mastery[kc_idx] = min(1.0, mastery[kc_idx] + 0.05 * (1 - mastery[kc_idx]))
                else:
                    mastery[kc_idx] = max(0.1, mastery[kc_idx] - 0.01)

                gap = rng.exponential(300) + 60
                current_time += gap

                if step % 20 == 19:
                    long_gap = rng.exponential(86400)
                    current_time += long_gap
                    decay = np.exp(-long_gap / (7 * 86400))
                    mastery *= decay
                    mastery = np.maximum(mastery, 0.1)

            sequences.append({
                "user_id": uid,
                "question_ids": np.array(question_ids, dtype=np.int64),
                "skill_ids": np.array(skill_ids, dtype=np.int64),
                "responses": np.array(responses, dtype=np.int64),
                "timestamps": np.array(timestamps, dtype=np.float64),
            })

        return sequences

    def _get_available_kcs(self, mastery: np.ndarray) -> list[int]:
        """根据先修关系和掌握度决定可选 KC。"""
        available = list(range(min(5, self.n_kcs)))
        for i in range(5, self.n_kcs):
            prereq_idx = max(0, i - 3)
            if mastery[prereq_idx] >= 0.4:
                available.append(i)
        return available if available else list(range(self.n_kcs))

    @staticmethod
    def _compute_prob_correct(ability: float, mastery: float, difficulty: float) -> float:
        """IRT 2PL + mastery 加成。"""
        theta = ability + mastery * 0.5
        logit = 2.0 * (theta - difficulty)
        return 1.0 / (1.0 + np.exp(-logit))

    def get_splits(
        self, sequences: list[dict], n_folds: int = 5, seed: int = 42
    ) -> list[tuple[list[dict], list[dict]]]:
        kf = KFold(n_splits=n_folds, shuffle=True, random_state=seed)
        indices = np.arange(len(sequences))
        splits = []
        for train_idx, val_idx in kf.split(indices):
            train_seqs = [sequences[i] for i in train_idx]
            val_seqs = [sequences[i] for i in val_idx]
            splits.append((train_seqs, val_seqs))
        return splits

    @property
    def n_questions(self) -> int:
        return self.n_problems + 1

    @property
    def n_skills(self) -> int:
        return self.n_kcs + 1

    @property
    def kc_names(self) -> list[str]:
        return [kc["name"] for kc in self.kcs]
