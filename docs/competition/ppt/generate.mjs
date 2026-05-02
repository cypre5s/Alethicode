import pptxgen from "pptxgenjs";

const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
pres.author = "Alethicode Team";
pres.title = "Alethicode——面向初学者的 LLM 驱动智能教育平台";

const C = {
  navy: "1E2761",
  ice: "CADCFC",
  white: "FFFFFF",
  accent: "409EFF",
  lightBg: "F5F7FA",
  text: "1A1A1A",
  muted: "666666",
};

function titleSlide(title, subtitle) {
  const s = pres.addSlide();
  s.background = { fill: C.navy };
  s.addText(title, {
    x: 0.8, y: 1.5, w: 8.4, h: 1.5,
    fontSize: 40, fontFace: "Microsoft YaHei", bold: true,
    color: C.white, align: "left",
  });
  s.addText(subtitle, {
    x: 0.8, y: 3.2, w: 8.4, h: 0.8,
    fontSize: 18, fontFace: "Microsoft YaHei",
    color: C.ice, align: "left",
  });
  s.addShape(pres.ShapeType.rect, {
    x: 0.8, y: 4.3, w: 2, h: 0.06, fill: { color: C.accent },
  });
}

function sectionSlide(title) {
  const s = pres.addSlide();
  s.background = { fill: C.navy };
  s.addText(title, {
    x: 0.8, y: 2, w: 8.4, h: 1.5,
    fontSize: 36, fontFace: "Microsoft YaHei", bold: true,
    color: C.white, align: "left",
  });
  s.addShape(pres.ShapeType.rect, {
    x: 0.8, y: 3.6, w: 1.5, h: 0.05, fill: { color: C.accent },
  });
}

function contentSlide(title, bullets) {
  const s = pres.addSlide();
  s.background = { fill: C.white };
  s.addText(title, {
    x: 0.8, y: 0.3, w: 8.4, h: 0.8,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: C.navy, align: "left",
  });
  s.addShape(pres.ShapeType.rect, {
    x: 0.8, y: 1.1, w: 8.4, h: 0.02, fill: { color: C.ice },
  });
  const textRows = bullets.map((b, i) => ({
    text: `${i + 1}. ${b}`,
    options: { breakLine: true, fontSize: 16, fontFace: "Microsoft YaHei", color: C.text, bullet: false, lineSpacingMultiple: 1.6 },
  }));
  s.addText(textRows, { x: 0.8, y: 1.4, w: 8.4, h: 3.8, valign: "top" });
}

function twoColSlide(title, leftItems, rightItems, leftTitle, rightTitle) {
  const s = pres.addSlide();
  s.background = { fill: C.white };
  s.addText(title, {
    x: 0.8, y: 0.3, w: 8.4, h: 0.8,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: C.navy, align: "left",
  });
  s.addShape(pres.ShapeType.rect, {
    x: 0.8, y: 1.1, w: 8.4, h: 0.02, fill: { color: C.ice },
  });
  if (leftTitle) {
    s.addText(leftTitle, { x: 0.8, y: 1.3, w: 4, h: 0.5, fontSize: 18, fontFace: "Microsoft YaHei", bold: true, color: C.accent });
  }
  const lRows = leftItems.map(b => ({ text: `• ${b}`, options: { breakLine: true, fontSize: 14, color: C.text, lineSpacingMultiple: 1.5 } }));
  s.addText(lRows, { x: 0.8, y: leftTitle ? 1.8 : 1.4, w: 4, h: 3.4, valign: "top", fontFace: "Microsoft YaHei" });
  if (rightTitle) {
    s.addText(rightTitle, { x: 5.3, y: 1.3, w: 4, h: 0.5, fontSize: 18, fontFace: "Microsoft YaHei", bold: true, color: C.accent });
  }
  const rRows = rightItems.map(b => ({ text: `• ${b}`, options: { breakLine: true, fontSize: 14, color: C.text, lineSpacingMultiple: 1.5 } }));
  s.addText(rRows, { x: 5.3, y: rightTitle ? 1.8 : 1.4, w: 4.2, h: 3.4, valign: "top", fontFace: "Microsoft YaHei" });
}

function statsSlide(title, stats) {
  const s = pres.addSlide();
  s.background = { fill: C.lightBg };
  s.addText(title, {
    x: 0.8, y: 0.3, w: 8.4, h: 0.8,
    fontSize: 28, fontFace: "Microsoft YaHei", bold: true,
    color: C.navy, align: "left",
  });
  const colW = 8.4 / stats.length;
  stats.forEach((st, i) => {
    s.addText(st.value, {
      x: 0.8 + i * colW, y: 1.8, w: colW, h: 1,
      fontSize: 36, fontFace: "Microsoft YaHei", bold: true,
      color: C.accent, align: "center",
    });
    s.addText(st.label, {
      x: 0.8 + i * colW, y: 2.8, w: colW, h: 0.6,
      fontSize: 14, fontFace: "Microsoft YaHei",
      color: C.muted, align: "center",
    });
  });
}

titleSlide(
  "Alethicode——面向初学者的 LLM 驱动智能教育平台",
  "比赛作品展示"
);

contentSlide("项目背景 — 三大教学痛点", [
  "传统 OJ 只返回 AC/WA 等结果，学生不知道自己具体错在哪里",
  "课堂课件与课后练习割裂，学生难以把知识点迁移到代码实现",
  "教师缺少班级层面的学习证据，难以及时识别薄弱点和风险学生",
  "初学者常常不知道下一步该做什么，缺少过程性学习引导",
]);

contentSlide("解决方案 — 当前项目的四条主线", [
  "学习任务与评测：题目、提交、自动判题，多语言支持（Python3/C/C++/Java）",
  "LLM 导学：做题页内嵌 6 阶段工作流，6 个 Agent（含编排调度）输出 11 类教学卡片",
  "课件问答：课程内容包 + 混合检索 + 引用式回答",
  "学习闭环：错题本、专项复习、班级数据看板与后台语言包管理",
]);

sectionSlide("核心技术一：做题页 LLM 导学");

contentSlide("6 阶段教学工作流", [
  "READING：审题引导，帮助学生读懂题意、输入输出与约束",
  "IDEATING：思路分析，帮助学生拆问题、找方法、想边界",
  "CODING：编码阶段，提供最小必要支持，不直接替学生完成代码",
  "ERROR_FEEDBACK：错误诊断，解释失败原因并给出修复方向",
  "AC_REVIEW / TRANSFER：通过复盘与迁移练习，帮助知识迁移",
]);

twoColSlide("6 Agent 编排 + 结构化输出", [
  "OrchestratorAgent：编排调度，分派请求到对应教学 Agent",
  "GuideAgent：审题与思路引导",
  "DiagnosticsAgent：错误诊断",
  "TransferAgent：AC 后总结与迁移练习",
  "MetacognitiveAgent / ChatAgent：学习反思与补充对话",
], [
  "11 类教学卡片：导学、诊断、执行解释、复盘、迁移等",
  "ReflectionService：对关键输出做结构与内容复查",
  "做题页强绑定题目上下文，减少空泛回答",
  "ReAct 等增强推理能力保留为 Beta 开关，不作为默认前提",
], "Agent 架构", "质量保障");

sectionSlide("核心技术二：课件知识问答");

contentSlide("课程内容包 + 混合检索问答", [
  "后台提供 12 阶段语言包初始化管线：从课件上传到内容发布全程可追踪",
  "课件被加工为页面文本、知识点、教学单元与问题资源",
  "问答链路采用关键词检索 + 向量检索，回答附带引用页码",
  "QA Grounding Critic 等能力保留为 Beta 开关，比赛演示以可核验主流程为准",
]);

sectionSlide("核心技术三：教师学情分析");

twoColSlide("教师端数据看板", [
  "班级学习脉搏：周度趋势与活跃度变化",
  "薄弱知识点识别：帮助教师快速定位教学盲区",
  "课程内容使用分析：把课件使用与做题行为关联起来",
  "周报生成：沉淀班级阶段性学习情况",
], [
  "风险学生识别：结合掌握度、活跃度与连续错误",
  "学生画像：从班级视角查看个体学习状态",
  "班级、题目、作业放在同一系统里联动",
  "强调真实可演示的教师侧页面，而不是隐藏入口或实验页",
], "班级分析", "教学支持");

sectionSlide("面向初学者的体验设计");

twoColSlide("学生侧学习闭环", [
  "错题本集中展示错误记录、根因分析与修复结果",
  "AI 反思帮助学生把“做错”转化为“学会了什么”",
  "专项复习包支持围绕同类错误集中再练",
  "首页仪表盘显示掌握度、今日复习和下一步建议",
], [
  "题目页 LLM 导学面板与代码编辑区同屏，减少界面切换成本",
  "课件问答作为一级入口，方便回看课堂知识点",
  "错题本支持筛选、导出和重做此题",
  "界面围绕初学者学习节奏设计，而非竞赛型重刷题体验",
], "学习工具", "交互体验");

statsSlide("技术规模", [
  { value: "362", label: "Java 源文件" },
  { value: "103", label: "Vue 组件" },
  { value: "306", label: "接口映射" },
  { value: "49", label: "数据库迁移" },
  { value: "11", label: "AI 卡片类型" },
]);

contentSlide("技术栈", [
  "后端：Spring Boot 3.4.4 + Java 21 + JPA/JdbcTemplate + WebSocket",
  "前端：Vue 3 + Vite 7 + Element Plus + CodeMirror 6 + ECharts",
  "数据库：PostgreSQL + pgvector + Redis",
  "判题：Judge Server Docker 沙箱",
  "工程支撑：Flyway、Swagger UI、Micrometer / Prometheus、start.sh 一键启动",
]);

titleSlide("谢谢", "Alethicode——面向初学者的 LLM 驱动智能教育平台");

await pres.writeFile({ fileName: "/home/cypress/Alethicode/docs/competition/ppt/alethicode.pptx" });
console.log("PPT generated successfully!");
