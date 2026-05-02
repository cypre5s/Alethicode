# Static Replacement Audit

- 生成时间：2026-03-31T02:28:48.112Z
- OJ 路由数：old=22 / new=22
- Admin 路由数：old=18 / new=18
- OJ API 导出数：old=172 / new=175
- Admin API 导出数：old=78 / new=76
- 差异文件总数：146

## 分类统计

| category | count |
| --- | ---: |
| pure_vue3_syntax_migration | 1 |
| semantic_adapter_or_runtime_bridge | 33 |
| runtime_behavior_change_or_manual_review | 112 |

## 差异明细

| path | change_type | category | reason |
| --- | --- | --- | --- |
| components | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| composables | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| i18n/index.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/admin/api.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/App.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/btn/Cancel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/btn/IconBtn.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/btn/Save.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/CodeMirror.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/InfoCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/KatexEditor.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/Panel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/SideMenu.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/admin/components/Simditor.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/simditorFileUpload.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/components/TopNav.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/elementPlusTheme.less | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/admin/index.html | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/admin/index.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/admin/router.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/admin/style.less | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/AIVariantReview.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/Announcement.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/Conf.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/Dashboard.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/admin/views/general/JudgeServer.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/KCManagement.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/Login.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/MisconceptionManagement.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/PreflightStats.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/PruneTestCase.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/ReviewQueue.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/general/User.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/Home.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/index.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/problem/ImportAndExport.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/problem/Problem.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/admin/views/problem/ProblemList.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/api.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/App.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/CodeMirror.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/components/ECharts.vue | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/components/mixins/emitter.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/components/mixins/form.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/components/mixins/index.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/components/mixins/problem.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/components/NavBar.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/Pagination.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/Panel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/PedagogyPanel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/skillProfile/KnowledgeStarMap.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/skillProfile/PracticeHeatmap.vue | changed | pure_vue3_syntax_migration | diff 限定在插槽、生命周期、事件修饰符、router API 等 Vue3 语法迁移 |
| pages/oj/components/skillProfile/ProblemRecommendations.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/skillProfile/SkillRadar.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/skillProfile/StarMapDetailPanel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/SubmissionRiver.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/verticalMenu/VerticalMenu.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/components/verticalMenu/VerticalMenuItem.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/index.html | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/index.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/router/index.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/router/routes.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/AIGeneratedProblems.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/classroom/AssignmentDetail.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/AssignmentGrading.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/ClassroomAssignment.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/ClassroomDetail.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/ClassroomList.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/CollaborativeCoding.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/classroom/index.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/classroom/JoinClassroom.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/LessonManagement.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/classroom/MonitorDashboard.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/classroom/monitorDashboardChartMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/classroom/monitorDashboardPlaybackMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/classroom/monitorDashboardUiHelperMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/general/Announcements.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/general/Home.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/general/NotFound.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/index.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/agentContracts.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/AITutorSidebar.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/astVisualizationMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/cards/ErrorDiagnosisCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/cards/ExecutionTraceExplainerCard.vue | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/views/problem/cards/FadedExampleCard.vue | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/views/problem/cards/IdeateAnalysisCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/cards/MinimalHintCard.vue | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/views/problem/cards/PostACCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/cards/ProblemGuideCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/cards/TransferProblemCard.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/cards/WorkedExampleCard.vue | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/views/problem/CodeAnalysisPanel.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/CodeEditorPanel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/frustrationMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/IdeatePanel.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/IdeateSidebar.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/ParsonsPanel.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/preflightDetectors.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/Problem.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/problem/ProblemDescription.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/ProblemList.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/problem/problemPresentationMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/submissionMixin.js | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/SubmissionPanel.vue | only_in_old | runtime_behavior_change_or_manual_review | frontend 缺少旧文件，需要人工确认是否已被合并或替代 |
| pages/oj/views/problem/UnifiedAgentPanel.vue | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/problem/workflowStateMachine.js | changed | semantic_adapter_or_runtime_bridge | diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致 |
| pages/oj/views/setting/children/AccountSetting.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/setting/children/ProfileSetting.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/setting/children/SecuritySetting.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/setting/index.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/setting/Settings.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/submission/SubmissionDetails.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/submission/SubmissionList.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/ApplyResetPassword.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/LearnerNotebook.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/learnerNotebookState.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| pages/oj/views/user/Login.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/Register.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/ResetPassword.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/StandaloneLogin.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/StandaloneRegister.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/views/user/UserHome.vue | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| pages/oj/viewUiPlusTheme.less | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| plugins/analytics.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| plugins/clipboard.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| plugins/highlight.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| plugins/katex.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| store/index.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| store/modules/problem.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| store/modules/user.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| styles/common.less | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/classroomConstants.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/echarts.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/filters.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/learningEventsTransport.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/notifications.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/runtimeEnv.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/runtimeErrorFilter.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/sanitize.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/sentry.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/settingsToast.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/time.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/twoFactorQrCode.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
| utils/utils.js | changed | runtime_behavior_change_or_manual_review | diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价 |
| utils/websocketUrl.js | only_in_new | semantic_adapter_or_runtime_bridge | frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换 |
