# 批次6：MonitorDashboard 页面垂直拆分闭环记录

## 1. 模块分析（rules: Phase 1）
- 输入：`frontend/src/pages/oj/views/classroom/MonitorDashboard.vue`。
- 处理流程：UI helper、回放控制、图表渲染全部耦合于单文件 methods。
- 状态变化：拆分 methods 到 mixin，保持按钮、弹窗、ws 展示语义不变。
- 输出：监控看板交互与显示语义保持一致。
- 上下游影响：
  - 上游课堂监控入口不变。
  - 下游 API 调用与 ws 数据消费路径不变。

## 2. 映射规划（rules: Phase 2）
| 旧结构 | 新结构 |
|---|---|
| `MonitorDashboard.vue` UI helper 方法 | `monitorDashboardUiHelperMixin.js` |
| `MonitorDashboard.vue` 回放方法 | `monitorDashboardPlaybackMixin.js` |
| `MonitorDashboard.vue` 图表方法 | `monitorDashboardChartMixin.js` |

## 3. 最短路径实现（rules: Phase 3）
- 新增 3 个 mixin（ui-helper/playback/chart）。
- `MonitorDashboard.vue` 引入 mixin 并移除重复方法块。
- 仅重组代码结构，不新增业务分支。

## 4. 测试验证（rules: Phase 4）
- `cd frontend && npm run test -- --runInBand` -> 通过。
- `cd frontend && node tests/test_frontend_smoke.js` -> 通过（关键文件存在与组件关键检查通过）。

## 5. code-reviewer 审查（rules: Phase 5）
- Security：无新外部输入执行路径。
- Performance：图表初始化与 resize 逻辑保持原调用频次。
- Correctness：播放控制（play/pause/seek）逻辑未漂移。
- Maintainability：看板方法按职责拆分，定位问题更快。
- 阻断项：0。

## 6. 结论
- 批次6完成闭环，MonitorDashboard 从单体 methods 拆为职责模块。
