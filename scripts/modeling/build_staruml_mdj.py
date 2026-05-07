"""
将 Alethicode 用例图与活动图导出为 StarUML 4 原生 .mdj 文件。

输出：
  · docs/architecture/alethicode-uml-models.mdj
       包含三个图：用例图 + 活动图 1（OJ 编程闭环）+ 活动图 2（AI 导学会话）
       学生 / 教师 / 管理员 三个 Actor、所有用例、四个泳道、include/extend/泛化关系，
       以及两张活动图的全部节点与控制流。

导入方式（StarUML 4）：
  File → Open → 选择 docs/architecture/alethicode-uml-models.mdj
"""

import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT = REPO_ROOT / "docs/architecture/alethicode-uml-models.mdj"


def _id(prefix: str) -> str:
    """生成 StarUML 风格的 _id。StarUML 接受任意非冲突字符串。"""
    if not hasattr(_id, "counter"):
        _id.counter = 0
    _id.counter += 1
    return f"AAAA-{prefix}-{_id.counter:04d}"


# 用例图模型元素定义。
PROJECT_ID = _id("PRJ")
MODEL_ID = _id("MODEL")
USECASE_DIAGRAM_ID = _id("UCDIAG")

# 三类参与者。
ACTOR_DEFS = [
    {"id": _id("ACT"), "name": "学生", "x": 60, "y": 200},
    {"id": _id("ACT"), "name": "教师", "x": 60, "y": 540},
    {"id": _id("ACT"), "name": "管理员", "x": 60, "y": 800},
]
ACTOR_BY_NAME = {a["name"]: a for a in ACTOR_DEFS}

# 四个泳道（用 UMLPackage 表示）
PACKAGE_DEFS = [
    {"id": _id("PKG"), "name": "基础 OJ"},
    {"id": _id("PKG"), "name": "AI 导学"},
    {"id": _id("PKG"), "name": "课件 / 学情 / 班级"},
    {"id": _id("PKG"), "name": "后台管理 / 运维"},
]
PKG_BY_NAME = {p["name"]: p for p in PACKAGE_DEFS}

# 用例定义含坐标，便于 StarUML 渲染。
USECASE_DEFS = [
    # 基础 OJ。
    {"name": "账户访问", "pkg": "基础 OJ", "x": 280, "y": 110},
    {"name": "浏览学习内容", "pkg": "基础 OJ", "x": 280, "y": 200},
    {"name": "OJ 编程闭环", "pkg": "基础 OJ", "x": 280, "y": 290},
    {"name": "真实判题", "pkg": "基础 OJ", "x": 280, "y": 380},
    {"name": "个人学习空间", "pkg": "基础 OJ", "x": 280, "y": 470},
    {"name": "数据隐私", "pkg": "基础 OJ", "x": 280, "y": 560},
    # AI 导学。
    {"name": "AI 导学会话", "pkg": "AI 导学", "x": 540, "y": 240},
    {"name": "审题导读", "pkg": "AI 导学", "x": 460, "y": 110},
    {"name": "思路分析", "pkg": "AI 导学", "x": 620, "y": 110},
    {"name": "骨架代码", "pkg": "AI 导学", "x": 460, "y": 380},
    {"name": "自由对话", "pkg": "AI 导学", "x": 620, "y": 380},
    {"name": "错误诊断", "pkg": "AI 导学", "x": 460, "y": 470},
    {"name": "通过复盘", "pkg": "AI 导学", "x": 620, "y": 470},
    {"name": "迁移练习", "pkg": "AI 导学", "x": 540, "y": 560},
    {"name": "检查点恢复", "pkg": "AI 导学", "x": 540, "y": 650},
    {"name": "可视化与代码拼装卡片", "pkg": "AI 导学", "x": 540, "y": 740},
    {"name": "学习画像标定", "pkg": "AI 导学", "x": 540, "y": 830},
    # 课件、学情与班级。
    {"name": "课件问答", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 110},
    {"name": "课件原页查看", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 200},
    {"name": "学情与自适应", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 290},
    {"name": "学习笔记本", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 380},
    {"name": "专项错题复习包", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 470},
    {"name": "班级教学", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 560},
    {"name": "作业流转", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 650},
    {"name": "课堂协作监控", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 740},
    {"name": "班级学情分析", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 830},
    {"name": "班级 AI 题目", "pkg": "课件 / 学情 / 班级", "x": 820, "y": 920},
    # 后台管理与运维。
    {"name": "题库与测试用例管理", "pkg": "后台管理 / 运维", "x": 1080, "y": 110},
    {"name": "知识点 KC 管理", "pkg": "后台管理 / 运维", "x": 1080, "y": 200},
    {"name": "AI 变体题 / 误解审核", "pkg": "后台管理 / 运维", "x": 1080, "y": 290},
    {"name": "课程语言包初始化", "pkg": "后台管理 / 运维", "x": 1080, "y": 380},
    {"name": "跨班级教学洞察", "pkg": "后台管理 / 运维", "x": 1080, "y": 470},
    {"name": "用户与公告管理", "pkg": "后台管理 / 运维", "x": 1080, "y": 560},
    {"name": "判题机管理", "pkg": "后台管理 / 运维", "x": 1080, "y": 650},
    {"name": "系统配置", "pkg": "后台管理 / 运维", "x": 1080, "y": 740},
    {"name": "AI 治理与观测", "pkg": "后台管理 / 运维", "x": 1080, "y": 830},
    {"name": "系统监控与基础设施密钥", "pkg": "后台管理 / 运维", "x": 1080, "y": 920},
]
for uc in USECASE_DEFS:
    uc["id"] = _id("UC")
UC_BY_NAME = {u["name"]: u for u in USECASE_DEFS}

# 参与者与用例的关联。
ASSOCIATIONS = []
def assoc(actor_name, uc_name):
    ASSOCIATIONS.append((ACTOR_BY_NAME[actor_name]["id"], UC_BY_NAME[uc_name]["id"], actor_name, uc_name))

# 学生
for uc in ["账户访问", "浏览学习内容", "OJ 编程闭环", "个人学习空间", "数据隐私",
           "AI 导学会话", "学习画像标定",
           "课件问答", "学情与自适应", "学习笔记本", "专项错题复习包",
           "班级教学", "作业流转"]:
    assoc("学生", uc)
# 教师
for uc in ["账户访问", "班级教学", "作业流转", "课堂协作监控", "班级学情分析",
           "班级 AI 题目", "题库与测试用例管理", "知识点 KC 管理",
           "AI 变体题 / 误解审核", "课程语言包初始化"]:
    assoc("教师", uc)
# 管理员
for uc in ["跨班级教学洞察", "用户与公告管理", "判题机管理", "系统配置",
           "AI 治理与观测", "系统监控与基础设施密钥"]:
    assoc("管理员", uc)

# 包含 / 扩展 关系
INCLUDES = [
    ("OJ 编程闭环", "真实判题"),
    ("AI 导学会话", "审题导读"),
    ("AI 导学会话", "思路分析"),
    ("AI 导学会话", "骨架代码"),
    ("AI 导学会话", "自由对话"),
    ("迁移练习", "检查点恢复"),
    ("检查点恢复", "可视化与代码拼装卡片"),
    ("课件问答", "课件原页查看"),
    ("学情与自适应", "学习笔记本"),
    ("班级教学", "作业流转"),
    ("班级教学", "课堂协作监控"),
    ("班级教学", "班级学情分析"),
    ("题库与测试用例管理", "知识点 KC 管理"),
    ("课程语言包初始化", "知识点 KC 管理"),
]
EXTENDS = [
    # 严格遵守 UML：(扩展用例, 基本用例)，箭头由扩展用例指向基本用例
    ("错误诊断", "AI 导学会话"),
    ("通过复盘", "AI 导学会话"),
    ("迁移练习", "通过复盘"),
    ("错误诊断", "真实判题"),       # 扩展点：判题失败
    ("通过复盘", "真实判题"),       # 扩展点：判题通过
    ("专项错题复习包", "学情与自适应"),
    ("班级 AI 题目", "班级教学"),
    ("AI 变体题 / 误解审核", "题库与测试用例管理"),
]
GENERALIZATIONS = [
    # 管理员继承教师全部用例
    (ACTOR_BY_NAME["管理员"]["id"], ACTOR_BY_NAME["教师"]["id"], "管理员", "教师"),
]


# 活动图1：OJ 编程提交与判题。
ACT1_DIAGRAM_ID = _id("ACTDIAG")
ACT1_NODES = [
    {"id": _id("ACT1N"), "type": "UMLInitialNode", "name": "", "x": 200, "y": 60},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "打开题目页", "x": 170, "y": 130},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "编写代码", "x": 170, "y": 200},
    {"id": _id("ACT1N"), "type": "UMLDecisionNode", "name": "是否调试？", "x": 200, "y": 270},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "提交调试请求", "x": 60, "y": 340},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "查看运行结果", "x": 60, "y": 410},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "提交代码", "x": 280, "y": 340},
    {"id": _id("ACT1N"), "type": "UMLObject", "name": "提交记录 [pending]", "x": 280, "y": 410},
    {"id": _id("ACT1N"), "type": "UMLForkNode", "name": "", "x": 200, "y": 480},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "Judge 沙箱执行", "x": 80, "y": 550},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "记录学习行为事件", "x": 320, "y": 550},
    {"id": _id("ACT1N"), "type": "UMLJoinNode", "name": "", "x": 200, "y": 620},
    {"id": _id("ACT1N"), "type": "UMLDecisionNode", "name": "判题结果？", "x": 200, "y": 690},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "标记 AC", "x": 60, "y": 760},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "记录 WA / CE / TLE", "x": 280, "y": 760},
    {"id": _id("ACT1N"), "type": "UMLMergeNode", "name": "", "x": 200, "y": 840},
    {"id": _id("ACT1N"), "type": "UMLAction", "name": "更新学情画像", "x": 170, "y": 910},
    {"id": _id("ACT1N"), "type": "UMLFinalNode", "name": "", "x": 200, "y": 990},
]
ACT1_FLOWS = [
    (0, 1, ""),
    (1, 2, ""),
    (2, 3, ""),
    (3, 4, "[需要调试]"),
    (3, 6, "[直接提交]"),
    (4, 5, ""),
    (5, 2, "[继续修改]"),
    (6, 7, ""),
    (7, 8, ""),
    (8, 9, ""),
    (8, 10, ""),
    (9, 11, ""),
    (10, 11, ""),
    (11, 12, ""),
    (12, 13, "[通过]"),
    (12, 14, "[失败]"),
    (13, 15, ""),
    (14, 15, ""),
    (15, 16, ""),
    (16, 17, ""),
]


# 活动图2：AI 导学会话工作流（含人工中断信号）。
ACT2_DIAGRAM_ID = _id("ACTDIAG")
ACT2_NODES = [
    {"id": _id("ACT2N"), "type": "UMLInitialNode", "name": "", "x": 200, "y": 60},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "学生进入题目页", "x": 160, "y": 130},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "创建 / 恢复会话", "x": 160, "y": 200},
    {"id": _id("ACT2N"), "type": "UMLDecisionNode", "name": "是否首次？", "x": 200, "y": 270},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "学习画像冷启动标定", "x": 50, "y": 340},
    {"id": _id("ACT2N"), "type": "UMLObject", "name": "EvidencePack [已组装]", "x": 320, "y": 340},
    {"id": _id("ACT2N"), "type": "UMLMergeNode", "name": "", "x": 200, "y": 420},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "选择教学动作 (TutorActionPolicy)", "x": 130, "y": 490},
    {"id": _id("ACT2N"), "type": "UMLForkNode", "name": "", "x": 200, "y": 570},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "调用 LLM 生成卡片", "x": 60, "y": 640},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "写 Trace / Eval 记录", "x": 320, "y": 640},
    {"id": _id("ACT2N"), "type": "UMLJoinNode", "name": "", "x": 200, "y": 720},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "Schema 强校验", "x": 160, "y": 790},
    {"id": _id("ACT2N"), "type": "UMLDecisionNode", "name": "是否需要人工确认？", "x": 200, "y": 860},
    {"id": _id("ACT2N"), "type": "UMLAcceptEventAction", "name": "等待中断响应", "x": 60, "y": 940},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "推送卡片到前端", "x": 320, "y": 940},
    {"id": _id("ACT2N"), "type": "UMLDecisionNode", "name": "学生反馈？", "x": 200, "y": 1020},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "保存检查点 / 进入下一阶段", "x": 60, "y": 1090},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "切换 phase 或重做", "x": 320, "y": 1090},
    {"id": _id("ACT2N"), "type": "UMLMergeNode", "name": "", "x": 200, "y": 1170},
    {"id": _id("ACT2N"), "type": "UMLDecisionNode", "name": "是否进入 TRANSFER？", "x": 200, "y": 1240},
    {"id": _id("ACT2N"), "type": "UMLAction", "name": "迁移练习推荐", "x": 60, "y": 1310},
    {"id": _id("ACT2N"), "type": "UMLFinalNode", "name": "", "x": 200, "y": 1390},
]
ACT2_FLOWS = [
    (0, 1, ""),
    (1, 2, ""),
    (2, 3, ""),
    (3, 4, "[首次进入]"),
    (3, 5, "[已有画像]"),
    (4, 6, ""),
    (5, 6, ""),
    (6, 7, ""),
    (7, 8, ""),
    (8, 9, ""),
    (8, 10, ""),
    (9, 11, ""),
    (10, 11, ""),
    (11, 12, ""),
    (12, 13, ""),
    (13, 14, "[需要确认]"),
    (13, 15, "[直接推送]"),
    (14, 15, ""),
    (15, 16, ""),
    (16, 17, "[helpful]"),
    (16, 18, "[unhelpful / confusing]"),
    (17, 19, ""),
    (18, 19, ""),
    (18, 7, "[切换 phase]"),
    (19, 20, ""),
    (20, 21, "[完成主链]"),
    (20, 7, "[继续主链]"),
    (21, 22, ""),
]


# 构造 .mdj JSON。
def build_mdj():
    model_owned = []

    # 学生 / 教师 / 管理员（UMLActor）
    for actor in ACTOR_DEFS:
        model_owned.append({
            "_type": "UMLActor",
            "_id": actor["id"],
            "_parent": {"$ref": MODEL_ID},
            "name": actor["name"],
        })

    # 四个 Package（泳道）+ 用例
    for pkg in PACKAGE_DEFS:
        pkg_use_cases = [u for u in USECASE_DEFS if u["pkg"] == pkg["name"]]
        pkg_owned = []
        for uc in pkg_use_cases:
            pkg_owned.append({
                "_type": "UMLUseCase",
                "_id": uc["id"],
                "_parent": {"$ref": pkg["id"]},
                "name": uc["name"],
            })
        model_owned.append({
            "_type": "UMLPackage",
            "_id": pkg["id"],
            "_parent": {"$ref": MODEL_ID},
            "name": pkg["name"],
            "ownedElements": pkg_owned,
        })

    # 关联（Association）
    for src_id, tgt_id, src_name, tgt_name in ASSOCIATIONS:
        assoc_id = _id("ASSOC")
        end1 = _id("END")
        end2 = _id("END")
        model_owned.append({
            "_type": "UMLAssociation",
            "_id": assoc_id,
            "_parent": {"$ref": MODEL_ID},
            "name": "",
            "end1": {
                "_type": "UMLAssociationEnd",
                "_id": end1,
                "_parent": {"$ref": assoc_id},
                "reference": {"$ref": src_id},
                "name": "",
            },
            "end2": {
                "_type": "UMLAssociationEnd",
                "_id": end2,
                "_parent": {"$ref": assoc_id},
                "reference": {"$ref": tgt_id},
                "name": "",
            },
        })

    # 包含关系。
    for src_name, tgt_name in INCLUDES:
        inc_id = _id("INC")
        model_owned.append({
            "_type": "UMLInclude",
            "_id": inc_id,
            "_parent": {"$ref": MODEL_ID},
            "source": {"$ref": UC_BY_NAME[src_name]["id"]},
            "target": {"$ref": UC_BY_NAME[tgt_name]["id"]},
        })

    # 扩展关系。
    for src_name, tgt_name in EXTENDS:
        ext_id = _id("EXT")
        model_owned.append({
            "_type": "UMLExtend",
            "_id": ext_id,
            "_parent": {"$ref": MODEL_ID},
            "source": {"$ref": UC_BY_NAME[src_name]["id"]},
            "target": {"$ref": UC_BY_NAME[tgt_name]["id"]},
        })

    # 泛化关系（管理员 → 教师）。
    for src_id, tgt_id, src_name, tgt_name in GENERALIZATIONS:
        gen_id = _id("GEN")
        model_owned.append({
            "_type": "UMLGeneralization",
            "_id": gen_id,
            "_parent": {"$ref": MODEL_ID},
            "source": {"$ref": src_id},
            "target": {"$ref": tgt_id},
        })

    # 用例图视图。
    use_case_views = []
    for actor in ACTOR_DEFS:
        use_case_views.append({
            "_type": "UMLActorView",
            "_id": _id("AV"),
            "_parent": {"$ref": USECASE_DIAGRAM_ID},
            "model": {"$ref": actor["id"]},
            "left": actor["x"],
            "top": actor["y"],
            "width": 50,
            "height": 80,
        })
    for uc in USECASE_DEFS:
        use_case_views.append({
            "_type": "UMLUseCaseView",
            "_id": _id("UCV"),
            "_parent": {"$ref": USECASE_DIAGRAM_ID},
            "model": {"$ref": uc["id"]},
            "left": uc["x"],
            "top": uc["y"],
            "width": 200,
            "height": 60,
        })

    use_case_diagram = {
        "_type": "UMLUseCaseDiagram",
        "_id": USECASE_DIAGRAM_ID,
        "_parent": {"$ref": MODEL_ID},
        "name": "Alethicode 用例图（学生 / 教师 / 管理员）",
        "ownedViews": use_case_views,
    }
    model_owned.append(use_case_diagram)

    # ---- 活动图 1 ----
    act1_owned = []
    act1_node_ids = []
    for n in ACT1_NODES:
        act1_owned.append({
            "_type": n["type"],
            "_id": n["id"],
            "_parent": {"$ref": MODEL_ID},
            "name": n["name"],
        })
        act1_node_ids.append(n["id"])
    act1_flow_objs = []
    for src_idx, tgt_idx, guard in ACT1_FLOWS:
        flow_id = _id("CF1")
        act1_flow_objs.append({
            "_type": "UMLControlFlow",
            "_id": flow_id,
            "_parent": {"$ref": MODEL_ID},
            "source": {"$ref": ACT1_NODES[src_idx]["id"]},
            "target": {"$ref": ACT1_NODES[tgt_idx]["id"]},
            "guard": guard,
        })
    model_owned.extend(act1_owned)
    model_owned.extend(act1_flow_objs)

    act1_views = []
    for n in ACT1_NODES:
        view_type = n["type"] + "View"
        act1_views.append({
            "_type": view_type,
            "_id": _id("AV1"),
            "_parent": {"$ref": ACT1_DIAGRAM_ID},
            "model": {"$ref": n["id"]},
            "left": n["x"],
            "top": n["y"],
            "width": 140 if n["type"] in ("UMLAction", "UMLObject") else 30,
            "height": 50 if n["type"] == "UMLAction" else 30,
        })
    model_owned.append({
        "_type": "UMLActivityDiagram",
        "_id": ACT1_DIAGRAM_ID,
        "_parent": {"$ref": MODEL_ID},
        "name": "活动图 1：OJ 编程闭环（编写 → 调试 → 提交 → 判题 → 学情更新）",
        "ownedViews": act1_views,
    })

    # ---- 活动图 2 ----
    act2_owned = []
    for n in ACT2_NODES:
        act2_owned.append({
            "_type": n["type"],
            "_id": n["id"],
            "_parent": {"$ref": MODEL_ID},
            "name": n["name"],
        })
    act2_flow_objs = []
    for src_idx, tgt_idx, guard in ACT2_FLOWS:
        flow_id = _id("CF2")
        act2_flow_objs.append({
            "_type": "UMLControlFlow",
            "_id": flow_id,
            "_parent": {"$ref": MODEL_ID},
            "source": {"$ref": ACT2_NODES[src_idx]["id"]},
            "target": {"$ref": ACT2_NODES[tgt_idx]["id"]},
            "guard": guard,
        })
    model_owned.extend(act2_owned)
    model_owned.extend(act2_flow_objs)

    act2_views = []
    for n in ACT2_NODES:
        view_type = n["type"] + "View"
        act2_views.append({
            "_type": view_type,
            "_id": _id("AV2"),
            "_parent": {"$ref": ACT2_DIAGRAM_ID},
            "model": {"$ref": n["id"]},
            "left": n["x"],
            "top": n["y"],
            "width": 160 if n["type"] in ("UMLAction", "UMLObject", "UMLAcceptEventAction") else 30,
            "height": 50 if n["type"] in ("UMLAction", "UMLAcceptEventAction") else 30,
        })
    model_owned.append({
        "_type": "UMLActivityDiagram",
        "_id": ACT2_DIAGRAM_ID,
        "_parent": {"$ref": MODEL_ID},
        "name": "活动图 2：AI 导学会话工作流（FSM phase 切换 / 中断响应）",
        "ownedViews": act2_views,
    })

    return {
        "_type": "Project",
        "_id": PROJECT_ID,
        "name": "Alethicode 用例与活动建模",
        "ownedElements": [
            {
                "_type": "UMLModel",
                "_id": MODEL_ID,
                "_parent": {"$ref": PROJECT_ID},
                "name": "Alethicode 模型",
                "ownedElements": model_owned,
            }
        ],
    }


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    data = build_mdj()
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"MDJ -> {OUT}")


if __name__ == "__main__":
    main()
