# research/

研究 / 实验项目集合。区别于 `services/`：
- `services/` 是**生产部署**的微服务
- `research/` 是**离线训练 / 实验 / 分析**的代码库，**不在生产路径上**

## 子目录

| 目录 | 用途 | 状态 |
|------|------|------|
| `nfk/` | Next-Frame-Knowledge（学情建模 / 知识追踪）训练与消融实验代码；输出 ONNX 模型供 backend 加载推理 | 持续迭代 |

## 调用约定

- `research/*` 不引入 backend / services 的运行时依赖
- 训练产出的模型（`.onnx` / `.pt`）通过 backend 配置项 `alethicode.nfk.model-path` 加载，不直接 import Python 代码
- 训练数据通过 `backend/.../NfkDataExportService` 导出脱敏 CSV 给 `research/nfk/data/preprocessor.py` 消费
- Java 端推理走 `backend/.../NfkInferenceService` 调用 ONNX Runtime；与训练侧通过**字段契约**对齐（不通过进程通信）

## 运行

每个研究项目独立 `requirements.txt` / `pyproject.toml`：

```bash
# nfk 本地快速验证
cd research/nfk
pip install -r requirements.txt
python run_local.py --quick

# nfk 上传到 AutoDL 训练
bash research/nfk/upload_autodl.sh root@region-1.autodl.pro:12345
```

## 新增研究项目

请遵守目录约定：
1. 每个项目独立子目录（如 `research/<topic>/`）
2. 子目录内含 `README.md`、`requirements.txt`、`__init__.py`（保证 Python 可 import）
3. 与生产代码的契约通过 backend service 类（`Nfk*Service`）显式声明
4. 不直接 import services / backend 包代码
