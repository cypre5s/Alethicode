"""
教育数据集下载器。

支持数据集:
  - ASSISTments 2009-2010 Skill Builder
  - EdNet KT1 (Riiid)

用法:
  python -m nfk.data.download --dataset assistments --output nfk/datasets
  python -m nfk.data.download --dataset ednet --output nfk/datasets
  python -m nfk.data.download --dataset all --output nfk/datasets
"""

from __future__ import annotations

import argparse
import gzip
import logging
import os
import shutil
import zipfile
from pathlib import Path
from urllib.request import urlopen, urlretrieve, Request

logger = logging.getLogger(__name__)

ASSISTMENTS_URLS = [
    "https://drive.google.com/uc?export=download&id=0B2X0QD6q79ZJUFU1cjYtdGhVNjg",
    "https://drive.google.com/uc?export=download&id=1NNXHFRxcArrU0ZJSb9BIL56vmUt5FhlE",
]

EDNET_KT1_URLS = [
    "http://base.ustc.edu.cn/data/EdNet/EdNet-KT1.zip",
]
EDNET_QUESTIONS_URL = "http://base.ustc.edu.cn/data/EdNet/contents/questions.csv"


def _download_file(url: str, dest: Path, desc: str = "") -> bool:
    """下载文件，支持重试。"""
    if dest.exists() and dest.stat().st_size > 0:
        logger.info(f"已存在: {dest}")
        return True

    dest.parent.mkdir(parents=True, exist_ok=True)
    logger.info(f"下载 {desc or url} -> {dest}")

    try:
        req = Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urlopen(req, timeout=120) as resp, open(dest, "wb") as f:
            shutil.copyfileobj(resp, f)
        logger.info(f"完成: {dest} ({dest.stat().st_size / 1024 / 1024:.1f} MB)")
        return True
    except Exception as e:
        logger.warning(f"下载失败 ({url}): {e}")
        if dest.exists():
            dest.unlink()
        return False


def download_assistments(output_dir: Path) -> Path | None:
    """下载 ASSISTments 2009-2010 Skill Builder 数据集。"""
    target_dir = output_dir / "assistments2009"

    if target_dir.exists():
        csv_files = sorted(target_dir.glob("*.csv"))
        if csv_files:
            logger.info(f"ASSISTments 数据集已存在: {csv_files[0]}")
            return csv_files[0]

    target_file = target_dir / "skill_builder_data.csv"
    target_dir.mkdir(parents=True, exist_ok=True)

    for url in ASSISTMENTS_URLS:
        if _download_file(url, target_file, "ASSISTments 2009"):
            if target_file.stat().st_size > 1000:
                return target_file
            target_file.unlink()

    logger.error(
        "ASSISTments 自动下载失败。请手动下载:\n"
        "  1. 访问 https://sites.google.com/site/assistmentsdata/datasets/2009-2010-assistment-data\n"
        "  2. 下载 skill_builder_data.csv\n"
        f"  3. 放到 {target_dir}/"
    )
    return None


def download_ednet(output_dir: Path, max_users: int = 5000) -> Path | None:
    """
    下载 EdNet KT1 数据集。

    EdNet 完整数据集很大（~1.4GB 压缩），这里下载一个子集。
    """
    target_dir = output_dir / "ednet"
    kt1_dir = target_dir / "KT1"
    questions_file = target_dir / "questions.csv"

    existing_files = list(kt1_dir.glob("u*.csv")) if kt1_dir.exists() else []
    if len(existing_files) >= 100 and questions_file.exists():
        logger.info(f"EdNet 数据集已存在: {target_dir} ({len(existing_files)} 用户)")
        return target_dir

    target_dir.mkdir(parents=True, exist_ok=True)
    kt1_dir.mkdir(parents=True, exist_ok=True)

    _download_file(EDNET_QUESTIONS_URL, questions_file, "EdNet questions.csv")

    for url in EDNET_KT1_URLS:
        ext = ".zip" if url.endswith(".zip") else ".tar.gz"
        archive_file = target_dir / f"KT1{ext}"
        if _download_file(url, archive_file, "EdNet KT1"):
            try:
                extracted_count = 0
                if ext == ".zip":
                    with zipfile.ZipFile(archive_file, "r") as zf:
                        csv_names = [n for n in zf.namelist() if n.endswith(".csv")]
                        csv_names = csv_names[:max_users]
                        for name in csv_names:
                            basename = os.path.basename(name)
                            if basename:
                                with zf.open(name) as src, open(kt1_dir / basename, "wb") as dst:
                                    shutil.copyfileobj(src, dst)
                                extracted_count += 1
                else:
                    import tarfile
                    with tarfile.open(archive_file, "r:gz") as tf:
                        members = [m for m in tf.getmembers() if m.name.endswith(".csv")]
                        members = members[:max_users]
                        for member in members:
                            member.name = os.path.basename(member.name)
                            # Python 3.12+ 推荐显式 filter 抵御 tar-slip；
                            # basename 已防止 path traversal，filter='data' 额外禁止硬链接/符号链接。
                            try:
                                tf.extract(member, kt1_dir, filter="data")
                            except TypeError:
                                tf.extract(member, kt1_dir)
                            extracted_count += 1
                logger.info(f"解压 {extracted_count} 个用户文件到 {kt1_dir}")
                archive_file.unlink()
                return target_dir
            except Exception as e:
                logger.warning(f"解压 EdNet 失败: {e}")

    logger.info(
        "EdNet KT1 自动下载失败（数据集约 1.16GB）。生成合成替代数据...\n"
        "如需真实数据，请手动下载:\n"
        "  1. 访问 http://base.ustc.edu.cn/data/EdNet/ 或 bit.ly/ednet_kt1\n"
        "  2. 下载 EdNet-KT1.zip\n"
        f"  3. 解压到 {kt1_dir}/ （保留 u*.csv 文件）\n"
        "  4. 下载 questions.csv 到同级目录"
    )
    _generate_ednet_sample(kt1_dir, questions_file, n_users=1000)
    return target_dir


def _generate_ednet_sample(
    kt1_dir: Path,
    questions_file: Path,
    n_users: int = 500,
) -> None:
    """
    当无法下载完整 EdNet 时，生成符合 EdNet 格式的合成样本数据。
    仅用于验证 pipeline 可用性。
    """
    import numpy as np

    logger.info(f"生成 EdNet 格式合成数据 ({n_users} 用户)...")

    n_questions = 200
    n_skills = 50

    if not questions_file.exists():
        with open(questions_file, "w") as f:
            f.write("question_id,bundle_id,tags,deployed_at,part\n")
            for q in range(n_questions):
                skills = ";".join([str(q % n_skills), str((q + 7) % n_skills)])
                f.write(f"q{q},{q // 5},{skills},2020-01-01,{q % 7 + 1}\n")

    correct_answers = {f"q{q}": chr(ord("a") + q % 4) for q in range(n_questions)}

    rng = np.random.RandomState(42)
    for uid in range(n_users):
        n_interactions = rng.randint(10, 150)
        timestamps = np.sort(rng.randint(1577836800, 1609459200, n_interactions))
        question_ids = rng.randint(0, n_questions, n_interactions)

        ability = rng.normal(0.5, 0.2)
        difficulties = rng.uniform(0.2, 0.8, n_questions)

        lines = ["timestamp,solving_id,question_id,user_answer,correct_answer,elapsed_time\n"]
        for i in range(n_interactions):
            qid = question_ids[i]
            q_str = f"q{qid}"
            correct_ans = correct_answers[q_str]
            prob_correct = 1 / (1 + np.exp(-(ability - difficulties[qid]) * 3))
            is_correct = int(rng.random() < prob_correct)
            if is_correct:
                user_ans = correct_ans
            else:
                wrong_choices = [c for c in "abcd" if c != correct_ans]
                user_ans = rng.choice(wrong_choices)
            elapsed = rng.randint(5000, 120000)
            lines.append(f"{timestamps[i]},{i},{q_str},{user_ans},{correct_ans},{elapsed}\n")

        user_file = kt1_dir / f"u{uid}.csv"
        with open(user_file, "w") as f:
            f.writelines(lines)

    logger.info(f"合成数据生成完毕: {kt1_dir}")


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    parser = argparse.ArgumentParser(description="教育数据集下载器")
    parser.add_argument(
        "--dataset", type=str, required=True,
        choices=["assistments", "ednet", "all"],
        help="要下载的数据集",
    )
    parser.add_argument(
        "--output", type=str, default="nfk/datasets",
        help="输出目录",
    )
    parser.add_argument(
        "--max-users", type=int, default=5000,
        help="EdNet 最大用户数",
    )
    args = parser.parse_args()

    output_dir = Path(args.output)

    if args.dataset in ("assistments", "all"):
        result = download_assistments(output_dir)
        if result:
            print(f"ASSISTments: {result}")

    if args.dataset in ("ednet", "all"):
        result = download_ednet(output_dir, max_users=args.max_users)
        if result:
            print(f"EdNet: {result}")


if __name__ == "__main__":
    main()
