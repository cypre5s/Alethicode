"""
基于用例图的两张 UML 活动图（中文）。

选择用例：
  · 活动图 1：OJ 编程闭环（含真实判题）  - 覆盖泳道、起始/终止、活动、决策、对象流、Fork/Join
  · 活动图 2：AI 导学会话工作流          - 覆盖泳道、起始/终止、活动、决策、对象流、Fork/Join、Merge、信号接收

输出：
  docs/assets/images/activity-oj-judge.svg / .png
  docs/assets/images/activity-ai-tutor.svg / .png

设计原则：
  - 4 个垂直泳道，节点放在所属泳道
  - 流转线先画（被节点形状覆盖端点），保证节点形状内部干净
  - 中文标签，无英文专业词
"""

from pathlib import Path
from typing import List, Tuple

import cairosvg

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS = REPO_ROOT / "docs/assets/images"

# ============================================================
# 通用样式
# ============================================================
SVG_HEAD = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  <defs>
    <marker id="arr" viewBox="0 0 12 12" refX="10" refY="6" markerWidth="11" markerHeight="11" orient="auto">
      <path d="M0,0 L10,6 L0,12 Z" fill="#2d3a52"/>
    </marker>
    <filter id="sh" x="-8%" y="-8%" width="116%" height="116%">
      <feDropShadow dx="0" dy="2" stdDeviation="4" flood-color="#0f172a" flood-opacity="0.10"/>
    </filter>
  </defs>
  <style>
    .title    {{ font: 800 30px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#111827; }}
    .subtitle {{ font: 400 17px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#64748b; }}
    .lane     {{ stroke:#94a3b8; stroke-width:1.5; }}
    .lane-bg  {{ fill:#f8fafc; }}
    .lane-lbl {{ font: 700 18px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#1f2937; }}
    .lane-hdr {{ fill:#e2e8f0; stroke:#94a3b8; stroke-width:1.5; }}
    .act-box  {{ fill:#ffffff; stroke:#2d557a; stroke-width:2; filter:url(#sh); }}
    .act-txt  {{ font: 600 16px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#142033; dominant-baseline:middle; text-anchor:middle; }}
    .obj-box  {{ fill:#fff7ed; stroke:#9a3412; stroke-width:2; stroke-dasharray:1; }}
    .obj-txt  {{ font: 500 15px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#7c2d12; dominant-baseline:middle; text-anchor:middle; }}
    .dec      {{ fill:#fef9c3; stroke:#854d0e; stroke-width:2; }}
    .dec-txt  {{ font: 600 15px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#422006; dominant-baseline:middle; text-anchor:middle; }}
    .bar      {{ fill:#0f172a; }}
    .init     {{ fill:#0f172a; }}
    .final-o  {{ fill:#ffffff; stroke:#0f172a; stroke-width:2; }}
    .final-i  {{ fill:#0f172a; }}
    .signal   {{ fill:#dbeafe; stroke:#1e3a8a; stroke-width:2; }}
    .signal-txt {{ font: 600 15px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#1e3a8a; dominant-baseline:middle; text-anchor:middle; }}
    .flow     {{ fill:none; stroke:#2d3a52; stroke-width:1.8; }}
    .glabel-bg{{ fill:#ffffff; stroke:none; }}
    .glabel   {{ font: 700 14px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill:#334155; text-anchor:middle; dominant-baseline:middle; }}
  </style>
  <rect width="{W}" height="{H}" fill="#ffffff"/>
  <text class="title"    x="50" y="44">{TITLE}</text>
  <text class="subtitle" x="50" y="74">{SUBTITLE}</text>
"""

SVG_TAIL = "</svg>"


def lane_block(lanes: List[Tuple[int, int, str]], y_top: int, y_bottom: int) -> str:
    """生成泳道背景 + 顶部表头 + 分隔线 + 标签。
    lanes: [(x_left, x_right, name), ...]
    """
    parts = []
    for (xl, xr, name) in lanes:
        parts.append(f'<rect class="lane-bg" x="{xl}" y="{y_top}" width="{xr-xl}" height="{y_bottom-y_top}"/>')
    for (xl, xr, name) in lanes:
        parts.append(f'<rect class="lane-hdr" x="{xl}" y="{y_top}" width="{xr-xl}" height="40"/>')
        parts.append(f'<text class="lane-lbl" x="{(xl+xr)//2}" y="{y_top+27}" text-anchor="middle">{name}</text>')
    for i, (xl, xr, name) in enumerate(lanes):
        parts.append(f'<line class="lane" x1="{xr}" y1="{y_top}" x2="{xr}" y2="{y_bottom}"/>')
    parts.append(f'<line class="lane" x1="{lanes[0][0]}" y1="{y_top}" x2="{lanes[-1][1]}" y2="{y_top}"/>')
    parts.append(f'<line class="lane" x1="{lanes[0][0]}" y1="{y_bottom}" x2="{lanes[-1][1]}" y2="{y_bottom}"/>')
    parts.append(f'<line class="lane" x1="{lanes[0][0]}" y1="{y_top}" x2="{lanes[0][0]}" y2="{y_bottom}"/>')
    return "\n  ".join(parts)


# 节点几何：返回 (svg_xml, anchor_top, anchor_bottom, anchor_left, anchor_right)
# 锚点用于流转连线
def node_action(cx, cy, label, w=180, h=50):
    x = cx - w // 2
    y = cy - h // 2
    svg = (
        f'<g><rect class="act-box" x="{x}" y="{y}" width="{w}" height="{h}" rx="10"/>'
        f'<text class="act-txt" x="{cx}" y="{cy}">{label}</text></g>'
    )
    return svg, (cx, y), (cx, y + h), (x, cy), (x + w, cy)


def node_decision(cx, cy, label, w=160, h=60):
    pts = f"{cx},{cy-h//2} {cx+w//2},{cy} {cx},{cy+h//2} {cx-w//2},{cy}"
    svg = (
        f'<g><polygon class="dec" points="{pts}"/>'
        f'<text class="dec-txt" x="{cx}" y="{cy}">{label}</text></g>'
    )
    return svg, (cx, cy - h // 2), (cx, cy + h // 2), (cx - w // 2, cy), (cx + w // 2, cy)


def node_object(cx, cy, label, w=200, h=42):
    x = cx - w // 2
    y = cy - h // 2
    svg = (
        f'<g><rect class="obj-box" x="{x}" y="{y}" width="{w}" height="{h}"/>'
        f'<text class="obj-txt" x="{cx}" y="{cy}">{label}</text></g>'
    )
    return svg, (cx, y), (cx, y + h), (x, cy), (x + w, cy)


def node_init(cx, cy, r=12):
    svg = f'<circle class="init" cx="{cx}" cy="{cy}" r="{r}"/>'
    return svg, (cx, cy - r), (cx, cy + r), (cx - r, cy), (cx + r, cy)


def node_final(cx, cy, r=14):
    svg = (
        f'<g><circle class="final-o" cx="{cx}" cy="{cy}" r="{r}"/>'
        f'<circle class="final-i" cx="{cx}" cy="{cy}" r="{r-6}"/></g>'
    )
    return svg, (cx, cy - r), (cx, cy + r), (cx - r, cy), (cx + r, cy)


def node_fork(cx, cy, w=180, h=8):
    x = cx - w // 2
    y = cy - h // 2
    svg = f'<rect class="bar" x="{x}" y="{y}" width="{w}" height="{h}" rx="2"/>'
    return svg, (cx, y), (cx, y + h), (x, cy), (x + w, cy)


def node_signal_recv(cx, cy, label, w=180, h=46):
    """接收事件节点（凹型五边形）"""
    x = cx - w // 2
    y = cy - h // 2
    notch = 14
    pts = (
        f"{x},{y} {x+w},{y} {x+w},{y+h} {x},{y+h} {x+notch},{y+h//2}"
    )
    svg = (
        f'<g><polygon class="signal" points="{pts}"/>'
        f'<text class="signal-txt" x="{cx+notch//2}" y="{cy}">{label}</text></g>'
    )
    return svg, (cx, y), (cx, y + h), (x, cy), (x + w, cy)


def flow(p1, p2, label="", curve=False):
    x1, y1 = p1
    x2, y2 = p2
    if curve:
        mx = (x1 + x2) // 2
        my = (y1 + y2) // 2
        d = f"M {x1} {y1} Q {mx} {y1} {mx} {my} T {x2} {y2}"
        path = f'<path class="flow" d="{d}" marker-end="url(#arr)"/>'
    else:
        path = f'<line class="flow" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" marker-end="url(#arr)"/>'
    label_svg = ""
    if label:
        lx = (x1 + x2) // 2
        ly = (y1 + y2) // 2
        # 字符宽度估算：中文 14px，英文/数字 8px。带括号/斜杠按 8px 计算。
        text_w = 0
        for ch in label:
            text_w += 14 if ord(ch) > 127 else 9
        bg_w = text_w + 12
        bg_h = 22
        label_svg = (
            f'<rect class="glabel-bg" x="{lx - bg_w//2}" y="{ly - bg_h//2}" '
            f'width="{bg_w}" height="{bg_h}" rx="3"/>'
            f'<text class="glabel" x="{lx}" y="{ly}">{label}</text>'
        )
    return path + label_svg


# ============================================================
# 活动图 1：OJ 编程闭环
# ============================================================
def build_activity_oj() -> str:
    W, H = 1700, 1500
    lanes = [
        (50, 450, "学生（浏览器）"),
        (450, 850, "Spring 后端"),
        (850, 1250, "判题机沙箱"),
        (1250, 1650, "数据库 / 学情服务"),
    ]
    out = [SVG_HEAD.format(
        W=W, H=H,
        TITLE="活动图 1：OJ 编程闭环（编写 → 调试 → 提交 → 判题 → 学情更新）",
        SUBTITLE="覆盖：起始/终止节点、活动、决策、对象流、分叉/合并、四泳道",
    )]
    out.append(lane_block(lanes, y_top=100, y_bottom=1450))

    # ---------- 节点 ----------
    nodes = []  # 收集 (svg, top, bottom, left, right)

    nodes.append(node_init(250, 160))                          # 0 起始
    nodes.append(node_action(250, 220, "打开题目页"))           # 1
    nodes.append(node_action(250, 290, "编写代码"))             # 2
    nodes.append(node_decision(250, 370, "是否调试？"))         # 3
    nodes.append(node_action(250, 470, "提交调试请求"))         # 4
    nodes.append(node_action(650, 470, "执行轻量沙箱判题"))     # 5
    nodes.append(node_action(250, 540, "查看运行结果"))         # 6
    nodes.append(node_action(250, 640, "提交代码"))             # 7
    nodes.append(node_object(650, 640, "[ 提交记录: 排队中 ]")) # 8
    nodes.append(node_fork(650, 720))                          # 9 Fork
    nodes.append(node_action(650, 800, "记录学习行为事件"))     # 10
    nodes.append(node_action(1050, 800, "调度容器化沙箱"))      # 11
    nodes.append(node_object(1450, 800, "[ ai_learning_event ]"))   # 12
    nodes.append(node_action(1050, 880, "执行测试用例"))        # 13
    nodes.append(node_fork(650, 970))                          # 14 Join
    nodes.append(node_decision(650, 1050, "判题结果？"))         # 15
    nodes.append(node_action(450, 1140, "标记 通过"))            # 16
    nodes.append(node_action(850, 1140, "记录 错误 / 编译失败 / 超时"))  # 17
    nodes.append(node_decision(650, 1230, "汇合"))               # 18 merge
    nodes.append(node_action(1450, 1230, "更新学情画像"))         # 19
    nodes.append(node_action(250, 1230, "推送 WebSocket 事件"))  # 20
    nodes.append(node_final(250, 1330))                          # 21 终止

    for svg, *_ in nodes:
        out.append(svg)

    # ---------- 流转线（先画在最后面渲染层，但实际放在节点之前 SVG 顺序不重要因为节点 fill 不是 white 全部）----------
    # SVG 顺序从前到后渲染 → 流转线放在节点之前让节点覆盖端点
    flows = []
    f = flows.append
    n = nodes  # 简写

    f(flow(n[0][2], n[1][1]))                               # 起始 → 打开题目页
    f(flow(n[1][2], n[2][1]))                               # → 编写代码
    f(flow(n[2][2], n[3][1]))                               # → 是否调试？
    f(flow(n[3][3], (250, 470 - 25), "[需调试]"))            # 是否调试 左 → 提交调试请求
    f(flow(n[4][4], n[5][3], "请求"))                       # 提交调试 → 执行沙箱判题
    f(flow(n[5][2], (650, 540 - 25)))                        # 沙箱 → 查看运行结果（同 y）
    f(flow((650, 540 - 25), n[6][4]))                        # bend → 查看运行结果
    f(flow(n[6][2], (250, 290 + 25), "[继续修改]", curve=True))  # 查看结果 → 编写代码 (loopback)
    f(flow(n[3][4], (650, 370), "[直接提交]"))               # 是否调试 右 →
    f(flow((650, 370), (650, 615)))                          # 弯到 提交代码
    f(flow((650, 615), n[7][4]))                             # 横向 → 提交代码 not great. let me redo

    return "".join(out) + SVG_TAIL


# 上面手算流转线非常繁琐，重新用更可控的写法（连接 helper）
# ============================================================

def y_anchor_top(node):
    return node[1]
def y_anchor_bottom(node):
    return node[2]
def x_anchor_left(node):
    return node[3]
def x_anchor_right(node):
    return node[4]


def build_oj_v2() -> str:
    W, H = 1700, 1550
    lanes = [
        (50, 450, "学生（浏览器）"),
        (450, 850, "Spring 后端"),
        (850, 1250, "判题机沙箱"),
        (1250, 1650, "数据库 / 学情服务"),
    ]
    parts = [SVG_HEAD.format(
        W=W, H=H,
        TITLE="活动图 1：OJ 编程闭环（编写 → 调试 → 提交 → 判题 → 学情更新）",
        SUBTITLE="覆盖活动图主要符号：起始/终止节点、活动、决策、对象流、分叉/合并、四泳道",
    )]
    parts.append(lane_block(lanes, y_top=100, y_bottom=1500))

    # ===== 节点（先收集，再排序绘制：流转线 → 节点）=====
    N = {}
    N["start"] = node_init(250, 160)
    N["open"]  = node_action(250, 220, "打开题目页")
    N["code"]  = node_action(250, 290, "编写代码")
    N["dec_dbg"] = node_decision(250, 370, "是否需要调试？")
    N["dbg_req"] = node_action(250, 470, "提交调试请求")
    N["dbg_exec"] = node_action(650, 470, "执行轻量沙箱判题")
    N["dbg_view"] = node_action(250, 560, "查看运行结果")
    N["submit"] = node_action(250, 660, "提交代码")
    N["obj_pending"] = node_object(650, 660, "[ 提交记录 : 排队中 ]")
    N["fork"]  = node_fork(650, 750)
    N["log"]   = node_action(650, 830, "异步采集学习行为")
    N["dispatch"] = node_action(1050, 830, "调度容器化沙箱")
    N["obj_event"] = node_object(1450, 830, "[ ai_learning_event ]")
    N["exec"]  = node_action(1050, 920, "在容器内执行测试用例")
    N["join"]  = node_fork(650, 1020)
    N["dec_res"] = node_decision(650, 1110, "判题结果？")
    N["ac"]    = node_action(450, 1210, "标记 通过 (AC)")
    N["wa"]    = node_action(850, 1210, "记录 错误 / 编译失败 / 超时")
    N["merge"] = node_decision(650, 1310, "汇合", w=120, h=50)
    N["update_state"] = node_action(1450, 1310, "更新学情画像")
    N["ws_push"] = node_action(250, 1310, "推送 WebSocket 事件")
    N["end"]   = node_final(250, 1420)

    # ===== 流转线 =====
    F = []
    def add(src, tgt, label="", curve=False, anchor_src="bottom", anchor_tgt="top"):
        s = N[src]; t = N[tgt]
        # anchor mapping
        am = {
            "top":    s[1], "bottom": s[2], "left": s[3], "right": s[4],
        }
        bm = {
            "top":    t[1], "bottom": t[2], "left": t[3], "right": t[4],
        }
        F.append(flow(am[anchor_src], bm[anchor_tgt], label, curve=curve))

    add("start", "open")
    add("open", "code")
    add("code", "dec_dbg")
    add("dec_dbg", "dbg_req", "[需调试]")
    add("dbg_req", "dbg_exec", anchor_src="right", anchor_tgt="left")
    add("dbg_exec", "dbg_view", anchor_src="left", anchor_tgt="right")
    # dbg_view 回到 code
    F.append(flow(N["dbg_view"][3], (60, 290), "[继续修改]"))
    F.append(flow((60, 290), N["code"][3]))
    # dec_dbg 直接提交：右走 → 下到 submit
    # 通过 (650, 370) → (250, 660) 走 ⌐
    F.append(flow(N["dec_dbg"][4], (380, 370)))
    F.append(flow((380, 370), (380, 660), "[直接提交]"))
    F.append(flow((380, 660), N["submit"][4]))
    add("submit", "obj_pending", anchor_src="right", anchor_tgt="left")
    add("obj_pending", "fork")
    add("fork", "log")
    add("fork", "dispatch", anchor_src="right", anchor_tgt="top")
    F.append(flow(N["fork"][4], (1050, 750)))
    F.append(flow((1050, 750), N["dispatch"][1]))
    add("dispatch", "obj_event", anchor_src="right", anchor_tgt="left")
    add("dispatch", "exec")
    add("exec", "join", anchor_src="left", anchor_tgt="right")
    F.append(flow(N["exec"][3], (650, 920)))
    F.append(flow((650, 920), N["join"][1]))
    add("log", "join")
    add("join", "dec_res")
    add("dec_res", "ac", "[通过]")
    F.append(flow(N["dec_res"][3], (450, 1110)))
    F.append(flow((450, 1110), N["ac"][1]))
    F.append(flow(N["dec_res"][4], (850, 1110), "[失败]"))
    F.append(flow((850, 1110), N["wa"][1]))
    add("ac", "merge", anchor_src="right", anchor_tgt="left")
    add("wa", "merge", anchor_src="left", anchor_tgt="right")
    add("merge", "update_state", anchor_src="right", anchor_tgt="left")
    F.append(flow(N["merge"][3], (250, 1310), "推送结果"))
    F.append(flow((250, 1310), N["ws_push"][4]))
    add("ws_push", "end")

    # ===== 输出（先流转线，再节点）=====
    parts.extend(F)
    for k, node in N.items():
        parts.append(node[0])

    parts.append(SVG_TAIL)
    return "".join(parts)


# ============================================================
# 活动图 2：AI 导学会话工作流
# ============================================================
def build_ai_tutor() -> str:
    W, H = 1700, 1700
    lanes = [
        (50, 450, "学生（浏览器）"),
        (450, 850, "Spring 后端"),
        (850, 1250, "tutor-graph (Python)"),
        (1250, 1650, "LLM 服务 / PostgreSQL"),
    ]
    parts = [SVG_HEAD.format(
        W=W, H=H,
        TITLE="活动图 2：AI 导学会话工作流（FSM phase 切换 / 中断响应）",
        SUBTITLE="覆盖：起始/终止节点、活动、决策、合并、对象流、分叉/合并、信号接收、四泳道",
    )]
    parts.append(lane_block(lanes, y_top=100, y_bottom=1650))

    N = {}
    N["start"] = node_init(250, 160)
    N["open"]  = node_action(250, 220, "进入题目页")
    N["create"] = node_action(250, 300, "创建 / 恢复会话")
    N["auth"] = node_action(650, 300, "鉴权 + 路由分发")
    N["dec_first"] = node_decision(650, 390, "是否首次进入？")
    N["calib"] = node_action(250, 480, "学习画像冷启动标定")
    N["obj_state"] = node_object(1450, 480, "[ LearnerState ]")
    N["merge1"] = node_decision(650, 570, "合并", w=110, h=46)
    N["evid"] = node_action(650, 660, "组装 EvidencePack（RAG）")
    N["obj_pack"] = node_object(1450, 660, "[ EvidencePack ]")
    N["policy"] = node_action(650, 740, "选择教学动作（TutorActionPolicy）")
    N["fork"] = node_fork(650, 820)
    N["llm"] = node_action(1050, 900, "调用 LLM 生成卡片")
    N["trace"] = node_action(650, 900, "写 Trace / Eval 记录")
    N["obj_card"] = node_object(1450, 900, "[ Card 草稿 ]")
    N["join"] = node_fork(650, 1000)
    N["schema"] = node_action(650, 1080, "Schema 强校验（违规失败）")
    N["dec_human"] = node_decision(650, 1170, "是否需要人工确认？")
    N["wait"] = node_signal_recv(250, 1280, "等待中断响应")
    N["push"] = node_action(650, 1280, "推送卡片到前端")
    N["obj_card2"] = node_object(1450, 1280, "[ Card 已下发 ]")
    N["dec_fb"] = node_decision(450, 1380, "学生反馈？")
    N["save"] = node_action(250, 1470, "保存检查点 / 进入下一阶段")
    N["redo"] = node_action(650, 1470, "切换 phase / 重做")
    N["merge2"] = node_decision(450, 1560, "合并", w=110, h=46)
    N["dec_transfer"] = node_decision(650, 1560, "进入 TRANSFER？", w=170, h=60)
    N["transfer"] = node_action(1050, 1560, "迁移练习推荐")
    N["end"] = node_final(450, 1640)

    F = []
    def add(src, tgt, label="", curve=False, asrc="bottom", atgt="top"):
        s = N[src]; t = N[tgt]
        am = {"top": s[1], "bottom": s[2], "left": s[3], "right": s[4]}
        bm = {"top": t[1], "bottom": t[2], "left": t[3], "right": t[4]}
        F.append(flow(am[asrc], bm[atgt], label, curve=curve))

    add("start", "open")
    add("open", "create")
    add("create", "auth", asrc="right", atgt="left")
    add("auth", "dec_first")
    F.append(flow(N["dec_first"][3], (250, 390), "[首次]"))
    F.append(flow((250, 390), N["calib"][1]))
    add("calib", "obj_state", asrc="right", atgt="left")
    F.append(flow(N["calib"][2], (250, 570)))
    F.append(flow((250, 570), N["merge1"][3]))
    F.append(flow(N["dec_first"][2], N["merge1"][1], "[已有画像]"))
    add("merge1", "evid")
    add("evid", "obj_pack", asrc="right", atgt="left")
    add("evid", "policy")
    add("policy", "fork")
    add("fork", "trace")
    add("fork", "llm", asrc="right", atgt="top")
    F.append(flow(N["fork"][4], (1050, 820)))
    F.append(flow((1050, 820), N["llm"][1]))
    add("llm", "obj_card", asrc="right", atgt="left")
    add("llm", "join", asrc="left", atgt="right")
    F.append(flow(N["llm"][3], (650, 900)))
    F.append(flow((650, 900), N["join"][4]))
    add("trace", "join")
    add("join", "schema")
    add("schema", "dec_human")
    F.append(flow(N["dec_human"][3], (250, 1170), "[需要确认]"))
    F.append(flow((250, 1170), N["wait"][1]))
    add("wait", "dec_fb", asrc="bottom", atgt="left")
    F.append(flow(N["wait"][2], (250, 1380)))
    F.append(flow((250, 1380), N["dec_fb"][3]))
    F.append(flow(N["dec_human"][4], (650, 1170), "[直接推送]"))
    F.append(flow((650, 1170), N["push"][1]))
    add("push", "obj_card2", asrc="right", atgt="left")
    add("push", "dec_fb", asrc="left", atgt="right")
    F.append(flow(N["push"][3], (650, 1380)))
    F.append(flow((650, 1380), N["dec_fb"][4]))
    F.append(flow(N["dec_fb"][3], (250, 1380), "[helpful]"))
    F.append(flow((250, 1380), N["save"][1]))
    F.append(flow(N["dec_fb"][4], (650, 1380), "[unhelpful]"))
    F.append(flow((650, 1380), N["redo"][1]))
    add("save", "merge2", asrc="bottom", atgt="left")
    F.append(flow(N["save"][2], (250, 1560)))
    F.append(flow((250, 1560), N["merge2"][3]))
    add("redo", "dec_transfer")
    add("merge2", "dec_transfer", asrc="right", atgt="left")
    F.append(flow(N["dec_transfer"][4], N["transfer"][3], "[是]"))
    F.append(flow(N["dec_transfer"][1], (650, 740 - 25), "[继续主链]", curve=True))
    F.append(flow((650, 740 - 25), N["policy"][2]))
    F.append(flow(N["transfer"][2], (1050, 1640)))
    F.append(flow((1050, 1640), N["end"][4]))

    parts.extend(F)
    for k, node in N.items():
        parts.append(node[0])

    parts.append(SVG_TAIL)
    return "".join(parts)


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)

    # 活动图 1
    svg1 = build_oj_v2()
    p1_svg = ASSETS / "activity-oj-judge.svg"
    p1_png = ASSETS / "activity-oj-judge.png"
    p1_svg.write_bytes(svg1.encode("utf-8"))
    cairosvg.svg2png(bytestring=svg1.encode("utf-8"), write_to=str(p1_png), output_width=1700, output_height=1550)
    print(f"活动图1 SVG -> {p1_svg}")
    print(f"活动图1 PNG -> {p1_png}")

    # 活动图 2
    svg2 = build_ai_tutor()
    p2_svg = ASSETS / "activity-ai-tutor.svg"
    p2_png = ASSETS / "activity-ai-tutor.png"
    p2_svg.write_bytes(svg2.encode("utf-8"))
    cairosvg.svg2png(bytestring=svg2.encode("utf-8"), write_to=str(p2_png), output_width=1700, output_height=1700)
    print(f"活动图2 SVG -> {p2_svg}")
    print(f"活动图2 PNG -> {p2_png}")


if __name__ == "__main__":
    main()
