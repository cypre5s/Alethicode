# Alethicode 易拉宝 · 生图 Prompt

> 对应版面文案：`docs/competition/rollup-banner.md`
> 目标工具：Midjourney / DALL-E 3 / Stable Diffusion SDXL / 豆包 / 通义万相 / 即梦 Dreamina 等
> 规格：80cm × 200cm 竖版（长宽比 2:5）· 印刷级 300 DPI
>
> **重要提示**：当前主流生图模型对大段中文字符的渲染普遍不稳定。本 prompt 已设计为**"底图 + 占位文字条"**策略 —— 让模型生成精美版式与视觉系统，中文字在后期用 Photoshop / Figma 替换到位。

---

## 🇨🇳 中文 Prompt（推荐给豆包 / 通义万相 / 即梦 / 秘塔 / Dreamina）

```text
学院派高端项目易拉宝海报设计，项目名 "Alethicode"，主题是面向大学 Python 初学者的 AI 导学系统。规格 80 厘米 × 200 厘米 竖版（长宽比 2:5），印刷级 300 DPI。

整体风格：极简编辑设计 / Editorial Poster 风格 / Stripe 与 Vercel 官网的简洁克制版式 / 学院派年度报告质感。构图基于严格栅格，大量留白，字体层级清晰。严禁渐变背景、投影、立体塑型、商业图库感。

配色：
- 底色：米白 #F7F3EC
- 主色：东华大学锦缎红 #A41E23
- 主体文字：深墨黑 #1A1B2E
- 金色点缀：#C9A46B（政策金句、校训、分隔线）
- 数字高光：沉稳蓝 #3C5BA6

版面从上到下 4 个区：
1. 上 25% Hero 区：顶部正中一条细金色分隔线，下方大字 Logo "Alethicode"（无衬线、粗重），Logo 下方预留 2 行副标题位置，底部再加一条细金色分隔线
2. 中上 25% 一句话区：居中一行粗体标题，两侧金色短分隔线，下方留白呼吸区
3. 中下 30% 三栏并列：左中右三栏等宽；每栏顶部一枚极简线稿图标（左栏：代码花括号 { } 包一颗心形；中栏：神经网络节点图，7 个节点用细线连成图；右栏：三层同心圆架构图），图标下方留数字爆点和简短说明区位
4. 底部 20%：深墨色块居左放 2-3 行政策金句（宋体金色字），色块右侧学校印章位（圆形徽章占位）+ 3 个 6×6cm 二维码方框占位

精细纹理：全图底层一层极淡的纺织提花暗纹（透明度 3-5%，呼应东华纺织学科底色），仅在肉眼贴近 1 米内可察觉。

图标风格：极简线稿，1.5pt 描边，无填色，金色或深墨色二选一。

字体建议（若模型无法稳定渲染中文，请用"灰色占位矩形条"代替所有中文字，便于后期在 PS 里替换）：大标题思源黑体 Heavy / 数据爆点思源宋体 Heavy / 副标题思源宋体 Regular / 小字思源黑体 Regular。

最终画面：如同在会议展厅现场拍摄的立式易拉宝，地面有淡灰阴影，背景为画廊级中性浅灰墙面，打光柔和均匀，无反光。

严禁元素：渐变光晕、霓虹色、大面积图片、三维立体模型、商业图库人像、卡通元素、emoji、像素感 UI 截图。

若要嵌入中文字（仅在模型擅长渲染中文时开启）：
- 大标题：Alethicode
- 副标：让不会写代码的大一新生，10 周后写出属于他的第一段代码
- 中轴一句话：AI 导学 · OJ 判题 · 学情贯通
- 三栏标题：第 2,801 位老师 / 7 个自研教育小模型 / 生产级工程底座
- 数据爆点：1,200+ · 78.9% · 13 分钟 · 99.97%
- 政策金句：推进人工智能全学段教育和全社会通识教育，源源不断培养高素质人才
- 校训：崇德博学 · 砺志尚实
- 落款：东华大学 · 信息与智能科学学院 · 2026
```

---

## 🇺🇸 English Prompt（推荐给 Midjourney / DALL-E 3 / SDXL）

```text
A premium academic rollup banner poster design, vertical format 80cm x 200cm (aspect ratio 2:5), print-ready 300 DPI, for "Alethicode" — an AI tutoring system for university Python learners.

Art direction: editorial minimalism, in the spirit of publications like Works That Work and Kinfolk, with interface aesthetic references to Stripe, Linear, and Vercel marketing sites. Strict grid-based composition, generous negative space, confident typography hierarchy, print-catalog restraint. Absolutely no gradients, no drop shadows, no skeuomorphic bevels, no stock photography.

Color palette:
- Warm off-white background: #F7F3EC
- Brocade red (Donghua University signature): #A41E23
- Deep ink black for body: #1A1B2E
- Muted gold for policy quotes / dividers / school marks: #C9A46B
- Grounded blue for numerical highlights: #3C5BA6

Layout, top to bottom, 4 zones:
1. Top 25% (Hero): thin gold horizontal rule on top, followed by a large sans-serif wordmark "Alethicode" in brocade red, two lines of subtitle placeholder below, closed by another thin gold rule
2. Middle-upper 25% (Statement): a single bold centered line with short gold rules on each side
3. Middle-lower 30% (Three columns): three equal-width columns with a minimalist line-icon at the top of each column — icon 1: code-braces wrapping a heart, icon 2: 7 neural network nodes connected by thin lines, icon 3: three concentric circles representing layered architecture. Below each icon, space for a bold numerical stat and a short caption
4. Bottom 20%: a deep-ink block on the left holding a two-or-three line policy quotation in gold serif type, a circular school crest placeholder on the right, plus three 6x6cm QR code placeholders

Texture: a whisper-faint jacquard pattern (inspired by traditional Chinese textile weaving, a subtle nod to Donghua University's textile heritage) applied at 3-5% opacity across the entire canvas. Visible only on close inspection.

Icons: minimal line art, 1.5pt stroke, no fills, in either gold or deep ink.

Typography reference (if Chinese cannot be reliably rendered, replace all Chinese passages with neutral grey placeholder bars so typesetting can happen in post): Source Han Sans Heavy for titles, Source Han Serif Heavy for numerical data, Source Han Serif Regular for policy quotations, Source Han Sans Regular for captions.

Final presentation: rendered as if photographed on location at a trade show booth — a freestanding roll-up banner stand, soft diffused lighting, neutral gallery grey wall behind, very faint floor shadow.

Do NOT use: neon glow, gradient halos, photographic human models, 3D renders, cartoon illustration, emojis, pixelated UI screenshots, cluttered stock elements.

Optional embedded text (only if the model handles Chinese well — otherwise leave as placeholder bars):
- Title: Alethicode
- Subtitle (CN): 让不会写代码的大一新生，10 周后写出属于他的第一段代码
- Middle statement (CN): AI 导学 · OJ 判题 · 学情贯通
- Column headers (CN): 第 2,801 位老师 / 7 个自研教育小模型 / 生产级工程底座
- Numerical highlights: 1,200+, 78.9%, 13 min, 99.97%
- Policy quote (CN): 推进人工智能全学段教育和全社会通识教育，源源不断培养高素质人才
- University motto (CN): 崇德博学 · 砺志尚实
- Attribution (CN): 东华大学 · 信息与智能科学学院 · 2026
```

---

## 模型参数建议

| 工具 | 推荐参数 |
|---|---|
| Midjourney v6+ | `--ar 2:5 --style raw --stylize 150 --v 6.1` |
| DALL-E 3（ChatGPT） | 在 prompt 开头注明 `portrait orientation, 1024x2560 ratio preview`；后期放大 |
| SDXL / Flux | `aspect_ratio=1:2.5`, steps 40-60, CFG 6-7, sampler DPM++ 2M Karras |
| 即梦 Dreamina | 选"海报/Poster" 模板，比例 2:5；中文字渲染较稳 |
| 通义万相 | 用"文字海报"模式；启用中文字稳渲染选项 |
| 豆包 Doubao | 中文提示全套直接粘贴；豆包对中文字敏感度高，但会受安全策略影响 |

---

## 后期流程（底图 + 文字）

1. 用上面 prompt 出 **3 张底图候选**（不同模型 / 不同 seed）
2. 选一张视觉系统最稳、留字位最清爽的
3. 在 Figma / Photoshop 里把所有中文替换到位（配合 `docs/competition/rollup-banner.md` 的文字稿）
4. 二维码用 [qrcode-monkey](https://www.qrcode-monkey.com/) 生成**深墨色 + 米白背景**，保证与底图配色一致
5. 印前 1 米目视测试（2 秒 A 区 / 5 秒 C 区三栏）
6. 送厂前把 PNG/PDF 导出成 300 DPI，CMYK 色彩空间，字体嵌入 / 描边外扩

---

## 一句话速用版（赶时间）

如果上面内容太长，可以直接粘贴这一句到生图工具（风格较弱但能出图）：

```text
A tall vertical academic rollup banner poster, 80x200cm, aspect ratio 2:5, editorial minimalist design like Stripe and Vercel marketing pages, warm off-white background #F7F3EC, deep brocade red #A41E23 for a large wordmark "Alethicode" at top, muted gold #C9A46B accent rules and quotations, deep ink body text, three-column lower section with minimalist line icons (code braces heart, neural network nodes, concentric circles), a dark block at the bottom with a gold serif policy quotation in Chinese and three 6cm QR code placeholders on the right. Subtle jacquard textile texture at 5% opacity. No gradients, no drop shadows, no stock photography, no 3D. Print-ready, photographed at a trade show booth with soft diffused light and neutral grey wall background.
```
