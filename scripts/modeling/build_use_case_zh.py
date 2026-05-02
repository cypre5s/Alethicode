"""
Alethicode 项目用例图（学生 / 教师 / 管理员）生成脚本

输入：本脚本里的 Python 字面量（中文 SVG 模板）
输出：
  · docs/assets/images/uml-use-case-zh.svg
  · docs/assets/images/uml-use-case-zh.png

之所以用 Python 而不是直接写 SVG 文件，是因为某些写入路径
会破坏 UTF-8 中文字节，这里强制以二进制 UTF-8 写入并校验。
"""

from pathlib import Path

import cairosvg

REPO_ROOT = Path(__file__).resolve().parents[2]
SVG_PATH = REPO_ROOT / "docs/assets/images/uml-use-case-zh.svg"
PNG_PATH = REPO_ROOT / "docs/assets/images/uml-use-case-zh.png"


SVG_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="2520" height="1500" viewBox="0 0 2520 1500">
  <defs>
    <marker id="openArrow" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto-start-reverse">
      <path d="M2,2 L10,6 L2,10" fill="none" stroke="#415167" stroke-width="1.8"/>
    </marker>
    <marker id="assocArrow" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="11" markerHeight="11" orient="auto-start-reverse">
      <path d="M0,0 L10,6 L0,12 Z" fill="#374151"/>
    </marker>
    <marker id="hollowTriangle" viewBox="0 0 14 14" refX="13" refY="7" markerWidth="14" markerHeight="14" orient="auto">
      <path d="M1,1 L13,7 L1,13 Z" fill="#fff" stroke="#111827" stroke-width="1.8"/>
    </marker>
    <filter id="shadow" x="-8%" y="-8%" width="116%" height="116%">
      <feDropShadow dx="0" dy="3" stdDeviation="6" flood-color="#0f172a" flood-opacity="0.08"/>
    </filter>
  </defs>
  <style>
    .title {{ font: 800 42px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #111827; }}
    .subtitle {{ font: 400 21px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #64748b; }}
    .boundary {{ fill: #f8fafc; stroke: #24364a; stroke-width: 3; filter: url(#shadow); }}
    .boundary-label {{ font: 800 28px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #1f2937; }}
    .lane-label {{ font: 800 21px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #334155; }}
    .lane {{ stroke: #d8dee8; stroke-width: 1.5; stroke-dasharray: 9 9; }}
    .usecase ellipse {{ fill: #fff; stroke: #2d557a; stroke-width: 2.5; }}
    .usecase text {{ font: 600 19px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #142033; dominant-baseline: middle; }}
    .usecase .uc-sub {{ font: 500 16px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #475569; }}
    .actor circle, .actor line {{ fill: #fff; stroke: #111827; stroke-width: 3.2; stroke-linecap: round; }}
    .actor text {{ font: 800 24px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #111827; }}
    .actor .actor-sub {{ font: 500 16px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #475569; }}
    .assoc line {{ stroke: #6b7280; stroke-width: 1.6; }}
    .dashed line {{ stroke: #415167; stroke-width: 1.8; stroke-dasharray: 8 7; }}
    .inherit line {{ stroke: #111827; stroke-width: 2.4; }}
    .inherit text {{ font: 700 16px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #111827; }}
    .edge-label {{ font: 700 16px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #2d3a52; paint-order: stroke; stroke: #f8fafc; stroke-width: 7px; stroke-linejoin: round; }}
    .legend-box {{ fill: #ffffff; stroke: #cbd5e1; stroke-width: 1.5; }}
    .legend-title {{ font: 700 17px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #1f2937; }}
    .legend-text {{ font: 500 15px 'Noto Sans CJK SC','Microsoft YaHei',Arial,sans-serif; fill: #334155; }}
  </style>

  <rect width="2520" height="1500" fill="#ffffff"/>
  <text class="title" x="80" y="66">Alethicode {TITLE}</text>
  <text class="subtitle" x="80" y="101">{SUBTITLE}</text>

  <rect class="boundary" x="350" y="130" width="2090" height="1320" rx="22"/>
  <text class="boundary-label" x="385" y="176">{BOUNDARY}</text>

  <line class="lane" x1="830"  y1="205" x2="830"  y2="1430"/>
  <line class="lane" x1="1370" y1="205" x2="1370" y2="1430"/>
  <line class="lane" x1="1900" y1="205" x2="1900" y2="1430"/>
  <text class="lane-label" x="540"  y="148">{LANE1}</text>
  <text class="lane-label" x="1050" y="148">{LANE2}</text>
  <text class="lane-label" x="1530" y="148">{LANE3}</text>
  <text class="lane-label" x="2070" y="148">{LANE4}</text>

  <!-- ============= 关联线（先画，被椭圆覆盖端点） ============= -->
  <!-- 包含 / 扩展 关系（端点已精确贴近椭圆边缘外侧；EXTEND 严格遵循 UML 方向：扩展用例 → 基本用例） -->
  <!-- include: OJ 编程闭环 → 真实判题（包含子用例） -->
  <g class="dashed">
    <line x1="590" y1="584" x2="590" y2="640" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="640" y="612" text-anchor="start">{INCLUDE}</text>
  </g>
  <!-- include: AI 导学会话 → 4 个 phase -->
  <g class="dashed">
    <line x1="990"  y1="375" x2="985"  y2="277" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="935" y="320" text-anchor="middle">{INCLUDE}</text>
  </g>
  <g class="dashed">
    <line x1="1210" y1="375" x2="1220" y2="277" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1285" y="320" text-anchor="middle">{INCLUDE}</text>
  </g>
  <g class="dashed">
    <line x1="990"  y1="420" x2="985"  y2="524" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="935" y="475" text-anchor="middle">{INCLUDE}</text>
  </g>
  <g class="dashed">
    <line x1="1210" y1="420" x2="1220" y2="524" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1285" y="475" text-anchor="middle">{INCLUDE}</text>
  </g>
  <!-- extend: 错误诊断 → AI 导学会话（错误诊断扩展会话） -->
  <g class="dashed">
    <line x1="950" y1="681" x2="1050" y2="446" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="975" y="555" text-anchor="middle">{EXTEND}</text>
  </g>
  <!-- extend: 通过复盘 → AI 导学会话 -->
  <g class="dashed">
    <line x1="1255" y1="681" x2="1155" y2="446" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1230" y="555" text-anchor="middle">{EXTEND}</text>
  </g>
  <!-- extend: 迁移练习 → 通过复盘（迁移在 AC 复盘扩展点上才触发） -->
  <g class="dashed">
    <line x1="1175" y1="818" x2="1255" y2="752" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1235" y="795" text-anchor="middle">{EXTEND}</text>
  </g>
  <!-- extend: 错误诊断 → 真实判题[判题失败] -->
  <g class="dashed">
    <line x1="835" y1="713" x2="750" y2="690" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="800" y="685" text-anchor="middle">{EXTEND_FAIL}</text>
  </g>
  <!-- extend: 通过复盘 → 真实判题[判题通过] -->
  <g class="dashed">
    <line x1="1140" y1="715" x2="750" y2="710" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="950" y="730" text-anchor="middle">{EXTEND_AC}</text>
  </g>
  <!-- include: 课件问答 → 课件原页查看 -->
  <g class="dashed">
    <line x1="1635" y1="282" x2="1635" y2="319" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1700" y="305" text-anchor="start">{INCLUDE}</text>
  </g>
  <!-- include: 学情与自适应 → 学习笔记本 -->
  <g class="dashed">
    <line x1="1635" y1="532" x2="1635" y2="572" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1700" y="555" text-anchor="start">{INCLUDE}</text>
  </g>
  <!-- extend: 专项错题复习包 → 学情与自适应 -->
  <g class="dashed">
    <line x1="1700" y1="694" x2="1700" y2="532" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1755" y="660" text-anchor="start">{EXTEND}</text>
  </g>
  <!-- include: 班级教学 → 作业流转 / 课堂监控 / 班级学情分析 -->
  <g class="dashed">
    <line x1="1635" y1="912" x2="1635" y2="952" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1700" y="935" text-anchor="start">{INCLUDE}</text>
  </g>
  <g class="dashed">
    <line x1="1700" y1="912" x2="1700" y2="1062" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1755" y="1010" text-anchor="start">{INCLUDE}</text>
  </g>
  <g class="dashed">
    <line x1="1700" y1="912" x2="1700" y2="1172" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1755" y="1140" text-anchor="start">{INCLUDE}</text>
  </g>
  <!-- extend: 班级 AI 题目 → 班级教学 -->
  <g class="dashed">
    <line x1="1635" y1="1287" x2="1635" y2="912" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1700" y="1100" text-anchor="start">{EXTEND}</text>
  </g>
  <!-- include: 题库与测试用例管理 → 知识点 KC 管理 -->
  <g class="dashed">
    <line x1="2170" y1="282" x2="2170" y2="319" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="2235" y="305" text-anchor="start">{INCLUDE}</text>
  </g>
  <!-- extend: AI 变体题 / 误解审核 → 题库与测试用例管理 -->
  <g class="dashed">
    <line x1="2235" y1="437" x2="2235" y2="282" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="2070" y="395" text-anchor="end">{EXTEND}</text>
  </g>
  <!-- include: 课程语言包初始化 → 知识点 KC 管理 -->
  <g class="dashed">
    <line x1="2092" y1="582" x2="2120" y2="394" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="2090" y="490" text-anchor="end">{INCLUDE}</text>
  </g>
  <!-- include: 迁移练习 → 检查点恢复（链式表达 AI 会话子能力） -->
  <g class="dashed">
    <line x1="1100" y1="891" x2="1100" y2="944" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1100" y="920" text-anchor="middle">{INCLUDE}</text>
  </g>
  <!-- include: 检查点恢复 → 可视化与代码拼装卡片 -->
  <g class="dashed">
    <line x1="1100" y1="1023" x2="1100" y2="1069" marker-end="url(#openArrow)"/>
    <text class="edge-label" x="1100" y="1048" text-anchor="middle">{INCLUDE}</text>
  </g>

  <!-- 学生（端点贴近 use case 左边缘外侧 5px，便于实线箭头清晰可见） -->
  <g class="assoc" marker-end="url(#assocArrow)">
    <line x1="217" y1="378" x2="430" y2="265"/>
    <line x1="217" y1="395" x2="427" y2="395"/>
    <line x1="217" y1="415" x2="425" y2="540"/>
    <line x1="217" y1="430" x2="430" y2="845"/>
    <line x1="217" y1="445" x2="440" y2="975"/>
    <line x1="217" y1="370" x2="915" y2="395"/>
    <line x1="217" y1="395" x2="1470" y2="240"/>
    <line x1="217" y1="408" x2="1460" y2="490"/>
    <line x1="217" y1="425" x2="1472" y2="610"/>
    <line x1="217" y1="445" x2="1488" y2="730"/>
    <line x1="217" y1="455" x2="1460" y2="870"/>
    <line x1="217" y1="465" x2="1470" y2="990"/>
    <line x1="217" y1="475" x2="940" y2="1240"/>
  </g>
  <!-- 教师 -->
  <g class="assoc" marker-end="url(#assocArrow)">
    <line x1="217" y1="800" x2="430" y2="265"/>
    <line x1="217" y1="820" x2="1460" y2="870"/>
    <line x1="217" y1="830" x2="1470" y2="990"/>
    <line x1="217" y1="840" x2="1460" y2="1100"/>
    <line x1="217" y1="850" x2="1470" y2="1210"/>
    <line x1="217" y1="860" x2="1470" y2="1325"/>
    <line x1="217" y1="780" x2="1995" y2="240"/>
    <line x1="217" y1="800" x2="2017" y2="355"/>
    <line x1="217" y1="815" x2="2007" y2="475"/>
    <line x1="217" y1="835" x2="1985" y2="620"/>
  </g>
  <!-- 管理员 独占 -->
  <g class="assoc" marker-end="url(#assocArrow)">
    <line x1="217" y1="1185" x2="2005" y2="755"/>
    <line x1="217" y1="1195" x2="2010" y2="895"/>
    <line x1="217" y1="1205" x2="2017" y2="1010"/>
    <line x1="217" y1="1215" x2="2005" y2="1125"/>
    <line x1="217" y1="1225" x2="1985" y2="1245"/>
    <line x1="217" y1="1235" x2="1985" y2="1370"/>
  </g>

  <!-- Actors -->
  <g class="actor">
    <circle cx="145" cy="400" r="26"/>
    <line x1="145" y1="426" x2="145" y2="500"/>
    <line x1="99"  y1="455" x2="191" y2="455"/>
    <line x1="145" y1="500" x2="100" y2="560"/>
    <line x1="145" y1="500" x2="190" y2="560"/>
    <text x="145" y="600" text-anchor="middle">{A_STUDENT}</text>
    <text class="actor-sub" x="145" y="624" text-anchor="middle">{A_STUDENT_SUB}</text>
  </g>
  <g class="actor">
    <circle cx="145" cy="820" r="26"/>
    <line x1="145" y1="846" x2="145" y2="920"/>
    <line x1="99"  y1="875" x2="191" y2="875"/>
    <line x1="145" y1="920" x2="100" y2="980"/>
    <line x1="145" y1="920" x2="190" y2="980"/>
    <text x="145" y="1020" text-anchor="middle">{A_TEACHER}</text>
    <text class="actor-sub" x="145" y="1044" text-anchor="middle">{A_TEACHER_SUB}</text>
  </g>
  <g class="actor">
    <circle cx="145" cy="1200" r="26"/>
    <line x1="145" y1="1226" x2="145" y2="1300"/>
    <line x1="99"  y1="1255" x2="191" y2="1255"/>
    <line x1="145" y1="1300" x2="100" y2="1360"/>
    <line x1="145" y1="1300" x2="190" y2="1360"/>
    <text x="145" y="1400" text-anchor="middle">{A_ADMIN}</text>
    <text class="actor-sub" x="145" y="1424" text-anchor="middle">{A_ADMIN_SUB}</text>
  </g>

  <g class="inherit">
    <line x1="65" y1="1170" x2="65" y2="930" marker-end="url(#hollowTriangle)"/>
    <text x="55" y="1050" text-anchor="end">{INHERIT}</text>
  </g>

  <!-- 1. 基础 OJ -->
  <g class="usecase"><ellipse cx="590" cy="265" rx="155" ry="44"/><text x="590" y="258" text-anchor="middle">{UC_AUTH}</text><text class="uc-sub" x="590" y="284" text-anchor="middle">{UC_AUTH_SUB}</text></g>
  <g class="usecase"><ellipse cx="590" cy="395" rx="158" ry="42"/><text x="590" y="388" text-anchor="middle">{UC_BROWSE}</text><text class="uc-sub" x="590" y="414" text-anchor="middle">{UC_BROWSE_SUB}</text></g>
  <g class="usecase"><ellipse cx="590" cy="540" rx="160" ry="44"/><text x="590" y="533" text-anchor="middle">{UC_OJ}</text><text class="uc-sub" x="590" y="559" text-anchor="middle">{UC_OJ_SUB}</text></g>
  <g class="usecase"><ellipse cx="590" cy="685" rx="160" ry="42"/><text x="590" y="678" text-anchor="middle">{UC_JUDGE}</text><text class="uc-sub" x="590" y="704" text-anchor="middle">{UC_JUDGE_SUB}</text></g>
  <g class="usecase"><ellipse cx="590" cy="845" rx="155" ry="42"/><text x="590" y="838" text-anchor="middle">{UC_PROFILE}</text><text class="uc-sub" x="590" y="864" text-anchor="middle">{UC_PROFILE_SUB}</text></g>
  <g class="usecase"><ellipse cx="590" cy="975" rx="145" ry="40"/><text x="590" y="968" text-anchor="middle">{UC_PRIVACY}</text><text class="uc-sub" x="590" y="994" text-anchor="middle">{UC_PRIVACY_SUB}</text></g>

  <!-- 2. AI Tutor -->
  <g class="usecase"><ellipse cx="1100" cy="395" rx="180" ry="48"/><text x="1100" y="388" text-anchor="middle">{UC_TUTOR}</text><text class="uc-sub" x="1100" y="414" text-anchor="middle">{UC_TUTOR_SUB}</text></g>
  <g class="usecase"><ellipse cx="950"  cy="240" rx="115" ry="34"/><text x="950"  y="246" text-anchor="middle">{UC_READ}</text></g>
  <g class="usecase"><ellipse cx="1255" cy="240" rx="115" ry="34"/><text x="1255" y="246" text-anchor="middle">{UC_IDEATE}</text></g>
  <g class="usecase"><ellipse cx="950"  cy="565" rx="135" ry="38"/><text x="950"  y="558" text-anchor="middle">{UC_SKEL}</text><text class="uc-sub" x="950" y="584" text-anchor="middle">{UC_SKEL_SUB}</text></g>
  <g class="usecase"><ellipse cx="1255" cy="565" rx="135" ry="38"/><text x="1255" y="558" text-anchor="middle">{UC_CHAT}</text><text class="uc-sub" x="1255" y="584" text-anchor="middle">{UC_CHAT_SUB}</text></g>
  <g class="usecase"><ellipse cx="950"  cy="715" rx="115" ry="34"/><text x="950"  y="722" text-anchor="middle">{UC_DIAG}</text></g>
  <g class="usecase"><ellipse cx="1255" cy="715" rx="115" ry="34"/><text x="1255" y="722" text-anchor="middle">{UC_AC}</text></g>
  <g class="usecase"><ellipse cx="1100" cy="855" rx="125" ry="36"/><text x="1100" y="861" text-anchor="middle">{UC_TRANSFER}</text></g>
  <g class="usecase"><ellipse cx="1100" cy="985" rx="142" ry="38"/><text x="1100" y="978" text-anchor="middle">{UC_CKPT}</text><text class="uc-sub" x="1100" y="1004" text-anchor="middle">{UC_CKPT_SUB}</text></g>
  <g class="usecase"><ellipse cx="1100" cy="1110" rx="160" ry="38"/><text x="1100" y="1103" text-anchor="middle">{UC_VIS}</text><text class="uc-sub" x="1100" y="1129" text-anchor="middle">{UC_VIS_SUB}</text></g>
  <g class="usecase"><ellipse cx="1100" cy="1240" rx="155" ry="38"/><text x="1100" y="1233" text-anchor="middle">{UC_CALIB}</text><text class="uc-sub" x="1100" y="1259" text-anchor="middle">{UC_CALIB_SUB}</text></g>

  <!-- 3. 课件 学情 班级 -->
  <g class="usecase"><ellipse cx="1635" cy="240"  rx="160" ry="42"/><text x="1635" y="233" text-anchor="middle">{UC_QA}</text><text class="uc-sub" x="1635" y="259" text-anchor="middle">{UC_QA_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="355"  rx="138" ry="36"/><text x="1635" y="349" text-anchor="middle">{UC_CITE}</text><text class="uc-sub" x="1635" y="375" text-anchor="middle">{UC_CITE_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="490"  rx="170" ry="42"/><text x="1635" y="483" text-anchor="middle">{UC_MASTERY}</text><text class="uc-sub" x="1635" y="509" text-anchor="middle">{UC_MASTERY_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="610"  rx="158" ry="38"/><text x="1635" y="603" text-anchor="middle">{UC_NB}</text><text class="uc-sub" x="1635" y="629" text-anchor="middle">{UC_NB_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="730"  rx="142" ry="36"/><text x="1635" y="736" text-anchor="middle">{UC_REVIEW}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="870"  rx="170" ry="42"/><text x="1635" y="863" text-anchor="middle">{UC_CLASS}</text><text class="uc-sub" x="1635" y="889" text-anchor="middle">{UC_CLASS_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="990"  rx="160" ry="38"/><text x="1635" y="983" text-anchor="middle">{UC_ASSIGN}</text><text class="uc-sub" x="1635" y="1009" text-anchor="middle">{UC_ASSIGN_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="1100" rx="170" ry="38"/><text x="1635" y="1093" text-anchor="middle">{UC_MON}</text><text class="uc-sub" x="1635" y="1119" text-anchor="middle">{UC_MON_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="1210" rx="160" ry="38"/><text x="1635" y="1203" text-anchor="middle">{UC_ANALYTICS}</text><text class="uc-sub" x="1635" y="1229" text-anchor="middle">{UC_ANALYTICS_SUB}</text></g>
  <g class="usecase"><ellipse cx="1635" cy="1325" rx="160" ry="38"/><text x="1635" y="1318" text-anchor="middle">{UC_AICLASS}</text><text class="uc-sub" x="1635" y="1344" text-anchor="middle">{UC_AICLASS_SUB}</text></g>

  <!-- 4. 后台 -->
  <g class="usecase"><ellipse cx="2170" cy="240"  rx="170" ry="42"/><text x="2170" y="233" text-anchor="middle">{UC_PROBADMIN}</text><text class="uc-sub" x="2170" y="259" text-anchor="middle">{UC_PROBADMIN_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="355"  rx="148" ry="36"/><text x="2170" y="362" text-anchor="middle">{UC_KC}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="475"  rx="158" ry="38"/><text x="2170" y="468" text-anchor="middle">{UC_AIVAR}</text><text class="uc-sub" x="2170" y="494" text-anchor="middle">{UC_AIVAR_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="620"  rx="180" ry="46"/><text x="2170" y="608" text-anchor="middle">{UC_LANGPACK}</text><text class="uc-sub" x="2170" y="634" text-anchor="middle">{UC_LANGPACK_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="755"  rx="160" ry="40"/><text x="2170" y="748" text-anchor="middle">{UC_INSIGHT}</text><text class="uc-sub" x="2170" y="774" text-anchor="middle">{UC_INSIGHT_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="895"  rx="155" ry="38"/><text x="2170" y="888" text-anchor="middle">{UC_USERADMIN}</text><text class="uc-sub" x="2170" y="914" text-anchor="middle">{UC_USERADMIN_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="1010" rx="148" ry="36"/><text x="2170" y="1003" text-anchor="middle">{UC_JUDGEADMIN}</text><text class="uc-sub" x="2170" y="1029" text-anchor="middle">{UC_JUDGEADMIN_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="1125" rx="160" ry="38"/><text x="2170" y="1118" text-anchor="middle">{UC_CONF}</text><text class="uc-sub" x="2170" y="1144" text-anchor="middle">{UC_CONF_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="1245" rx="180" ry="40"/><text x="2170" y="1238" text-anchor="middle">{UC_AIOPS}</text><text class="uc-sub" x="2170" y="1264" text-anchor="middle">{UC_AIOPS_SUB}</text></g>
  <g class="usecase"><ellipse cx="2170" cy="1370" rx="180" ry="40"/><text x="2170" y="1363" text-anchor="middle">{UC_OPS}</text><text class="uc-sub" x="2170" y="1389" text-anchor="middle">{UC_OPS_SUB}</text></g>

  <!-- 图例 -->
  <g>
    <rect class="legend-box" x="2120" y="40" width="380" height="120" rx="10"/>
    <text class="legend-title" x="2138" y="65">{LEG_TITLE}</text>
    <line x1="2138" y1="84" x2="2178" y2="84" stroke="#374151" stroke-width="1.6" marker-end="url(#assocArrow)"/>
    <text class="legend-text" x="2188" y="89">{LEG_ASSOC}</text>
    <line x1="2138" y1="106" x2="2178" y2="106" stroke="#415167" stroke-width="1.8" stroke-dasharray="8 7" marker-end="url(#openArrow)"/>
    <text class="legend-text" x="2188" y="111">{LEG_REL}</text>
    <line x1="2138" y1="130" x2="2163" y2="130" stroke="#111827" stroke-width="2.4"/>
    <path d="M2163,124 L2178,130 L2163,136 Z" fill="#fff" stroke="#111827" stroke-width="1.8"/>
    <text class="legend-text" x="2188" y="135">{LEG_INHERIT}</text>
  </g>
</svg>
"""


# 中文文本字典
TEXT = {
    "TITLE": "项目用例图",
    "SUBTITLE": "三类参与者：学生 / 教师 / 管理员；横向四个用例分组；管理员通过角色泛化继承教师全部用例",
    "BOUNDARY": "Alethicode OJ + AI 教学平台",
    "LANE1": "基础 OJ",
    "LANE2": "AI 导学",
    "LANE3": "课件 / 学情 / 班级",
    "LANE4": "后台管理 / 运维",

    "A_STUDENT": "学生",
    "A_STUDENT_SUB": "非计算机专业初学者",
    "A_TEACHER": "教师",
    "A_TEACHER_SUB": "任课老师 / 助教",
    "A_ADMIN": "管理员",
    "A_ADMIN_SUB": "平台 / 教研 / 系统",
    "INHERIT": "角色泛化",

    "UC_AUTH": "账户访问",
    "UC_AUTH_SUB": "注册 / 登录 / 找回密码 / 双因素",
    "UC_BROWSE": "浏览学习内容",
    "UC_BROWSE_SUB": "公告 / 题库 / 题目详情 / 标签",
    "UC_OJ": "OJ 编程闭环",
    "UC_OJ_SUB": "编写 / 调试 / 提交 / 历史",
    "UC_JUDGE": "真实判题",
    "UC_JUDGE_SUB": "通过 / 错误 / 编译失败 / 超时",
    "UC_PROFILE": "个人学习空间",
    "UC_PROFILE_SUB": "主页 / 设置 / 学习者笔记",
    "UC_PRIVACY": "数据隐私",
    "UC_PRIVACY_SUB": "数据导出 / 账户注销",

    "UC_TUTOR": "AI 导学会话",
    "UC_TUTOR_SUB": "题目页统一入口",
    "UC_READ": "审题导读",
    "UC_IDEATE": "思路分析",
    "UC_SKEL": "骨架代码",
    "UC_SKEL_SUB": "代码拼装练习",
    "UC_CHAT": "自由对话",
    "UC_CHAT_SUB": "知识回顾 / 答疑",
    "UC_DIAG": "错误诊断",
    "UC_AC": "通过复盘",
    "UC_TRANSFER": "迁移练习",
    "UC_CKPT": "检查点恢复",
    "UC_CKPT_SUB": "人工确认中断",
    "UC_VIS": "可视化与代码拼装卡片",
    "UC_VIS_SUB": "变量追踪 / 代码块拼装",
    "UC_CALIB": "学习画像标定",
    "UC_CALIB_SUB": "冷启动 / 跳过 / 应答",

    "UC_QA": "课件问答",
    "UC_QA_SUB": "基于课程语言包",
    "UC_CITE": "课件原页查看",
    "UC_CITE_SUB": "PDF 引用",
    "UC_MASTERY": "学情与自适应",
    "UC_MASTERY_SUB": "技能雷达 / 掌握度 / 推荐题",
    "UC_NB": "学习笔记本",
    "UC_NB_SUB": "反思生成 / 周报 / 班级高频",
    "UC_REVIEW": "专项错题复习包",
    "UC_CLASS": "班级教学",
    "UC_CLASS_SUB": "创建 / 加入 / 邀请码 / 成员",
    "UC_ASSIGN": "作业流转",
    "UC_ASSIGN_SUB": "发布 / 提交 / 批改",
    "UC_MON": "课堂协作监控",
    "UC_MON_SUB": "实时 / 错误聚类 / 干预",
    "UC_ANALYTICS": "班级学情分析",
    "UC_ANALYTICS_SUB": "周脉冲 / 风险学生",
    "UC_AICLASS": "班级 AI 题目",
    "UC_AICLASS_SUB": "生成 / 审核 / 发布",

    "UC_PROBADMIN": "题库与测试用例管理",
    "UC_PROBADMIN_SUB": "增删改查 / 导入导出",
    "UC_KC": "知识点 KC 管理",
    "UC_AIVAR": "AI 变体题 / 误解审核",
    "UC_AIVAR_SUB": "批准 / 驳回 / 合并",
    "UC_LANGPACK": "课程语言包初始化",
    "UC_LANGPACK_SUB": "上传课件 / 解析 / 抽取 KC / 生成题",
    "UC_INSIGHT": "跨班级教学洞察",
    "UC_INSIGHT_SUB": "全平台学情 / 风险排名",
    "UC_USERADMIN": "用户与公告管理",
    "UC_USERADMIN_SUB": "账号 / 公告",
    "UC_JUDGEADMIN": "判题机管理",
    "UC_JUDGEADMIN_SUB": "增删改 / 心跳",
    "UC_CONF": "系统配置",
    "UC_CONF_SUB": "邮件 / 验证码 / 注册开关",
    "UC_AIOPS": "AI 治理与观测",
    "UC_AIOPS_SUB": "调用链路 / 质量 / 灰度 / 公测反馈",
    "UC_OPS": "系统监控与基础设施密钥",
    "UC_OPS_SUB": "监控面板 / 路径 / 训练数据",

    "INCLUDE": "《include》",
    "EXTEND": "《extend》",
    "EXTEND_FAIL": "《extend》[判题失败]",
    "EXTEND_AC": "《extend》[判题通过]",

    "LEG_TITLE": "图例",
    "LEG_ASSOC": "参与者关联（实线箭头）",
    "LEG_REL": "《include》/《extend》（虚线箭头）",
    "LEG_INHERIT": "角色泛化（空心三角，子→父）",
}


def main() -> None:
    svg = SVG_TEMPLATE.format(**TEXT)
    SVG_PATH.write_bytes(svg.encode("utf-8"))
    cairosvg.svg2png(
        bytestring=svg.encode("utf-8"),
        write_to=str(PNG_PATH),
        output_width=2520,
        output_height=1500,
    )
    print(f"SVG -> {SVG_PATH}")
    print(f"PNG -> {PNG_PATH}")


if __name__ == "__main__":
    main()
