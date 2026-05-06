<template>
  <div class="problem-container" ref="problemContainer">
    <div id="problem-left" :style="leftPanelStyle">
      <OjPanel shadow style="margin: 0; height: 100%; display: flex; flex-direction: column; padding: 20px 30px 30px 30px;">
        <template #title><div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0; width: 100%;">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span>{{problem.title}}</span>
            <el-popover trigger="hover" placement="right" width="300">
              <template #reference>
                <el-icon :size="20" style="cursor: pointer; color: #2d8cf0;"><InfoFilled /></el-icon>
              </template>
              <template #default><div class="problem-info-popover">
                <div class="info-item">
                  <span class="info-label">ID:</span>
                  <span class="info-value">{{problem._id}}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{$t('m.Time_Limit')}}:</span>
                  <span class="info-value">{{problem.time_limit}}MS</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{$t('m.Memory_Limit')}}:</span>
                  <span class="info-value">{{problem.memory_limit}}MB</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{$t('m.IOMode')}}:</span>
                  <span class="info-value">{{problem.io_mode.io_mode}}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{$t('m.Created')}}:</span>
                  <span class="info-value">{{problem.created_by.username}}</span>
                </div>
                <div class="info-item" v-if="problem.difficulty">
                  <span class="info-label">{{$t('m.Level')}}:</span>
                  <span class="info-value">{{$t('m.' + problem.difficulty)}}</span>
                </div>
                <div class="info-item" v-if="problem.total_score">
                  <span class="info-label">{{$t('m.Score')}}:</span>
                  <span class="info-value">{{problem.total_score}}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{$t('m.Tags')}}:</span>
                  <span class="info-value">
                    <el-tag v-for="tag in problem.tags" :key="tag" size="default">{{tag}}</el-tag>
                  </span>
                </div>
              </div></template>
            </el-popover>
          </div>
        </div></template>
        <div v-if="problem.kc_names && problem.kc_names.length" class="kc-tag-row">
          <el-tooltip v-for="kc in problem.kc_names" :key="kc.kc_id" :content="kc.name + (kc.mastery != null ? ' (掌握度: ' + Math.round(kc.mastery * 100) + '%)' : '')" placement="top">
            <el-tag :type="kcElType(kc)" size="default" style="margin: 2px 4px 2px 0; cursor: default;">{{ kc.name }}</el-tag>
          </el-tooltip>
        </div>
        <div id="problem-content" class="markdown-body" style="flex: 1; overflow-y: auto; overflow-x: hidden;">
          <div>
            <p class="title">{{$t('m.Description')}}</p>
            <p v-katex class="content" v-html="renderMarkdown(problem.description)"></p>
            
            <p class="title">{{$t('m.Input')}} <span v-if="problem.io_mode.io_mode=='File IO'">({{$t('m.FromFile')}}: {{ problem.io_mode.input }})</span></p>
            <p v-katex class="content" v-html="renderMarkdown(problem.input_description)"></p>

            <p class="title">{{$t('m.Output')}} <span v-if="problem.io_mode.io_mode=='File IO'">({{$t('m.ToFile')}}: {{ problem.io_mode.output }})</span></p>
            <p v-katex class="content" v-html="renderMarkdown(problem.output_description)"></p>
            <div v-if="hasDatasetDownload" style="margin: 10px 0 14px;">
              <el-button type="primary" size="small" @click="downloadDataset">
                <el-icon><Download /></el-icon>下载数据集
              </el-button>
            </div>

            <div v-for="(sample, index) of problem.samples" :key="index">
              <div class="sample-block">
                <div class="sample-item">
                  <p class="title">{{$t('m.Sample_Input')}} {{index + 1}}</p>
                  <div class="sample-pre-wrap">
                    <button
                      type="button"
                      class="sample-copy-btn"
                      aria-label="复制输入样例"
                      v-clipboard:copy="normalizeSampleText(sample.input)"
                      v-clipboard:success="onCopy"
                      v-clipboard:error="onCopyError"
                    >
                      <el-icon :size="16"><DocumentCopy /></el-icon>
                    </button>
                    <pre>{{normalizeSampleText(sample.input)}}</pre>
                  </div>
                </div>
                <div class="sample-item">
                  <p class="title">{{$t('m.Sample_Output')}} {{index + 1}}</p>
                  <pre>{{normalizeSampleText(sample.output)}}</pre>
                </div>
              </div>
            </div>

            <div v-if="problem.hint">
              <p class="title" @click="hintVisible = !hintVisible" style="cursor: pointer; display: flex; align-items: center; user-select: none;">
                {{$t('m.Hint')}}
                <el-icon :style="{transform: hintVisible ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.2s', marginLeft: '5px'}"><ArrowDown /></el-icon>
              </p>
              <el-card shadow="never" v-show="hintVisible">
                <div v-katex class="content" v-html="renderMarkdown(problem.hint)"></div>
              </el-card>
            </div>

            <div v-if="problem.source">
              <p class="title">{{$t('m.Source')}}</p>
              <p class="content">{{problem.source}}</p>
            </div>
          </div>
        </div>
      </OjPanel>
      <transition name="slide-up">
        <div v-if="showIdleExamples && idleExamples.length" class="idle-examples-panel">
          <div class="idle-examples-header">
            <span class="idle-examples-title">
              <el-icon :size="18"><DocumentCopy /></el-icon>
              相关课件例题
            </span>
            <button type="button" class="idle-examples-close" aria-label="关闭相关课件例题" @click="dismissIdleExamples">&times;</button>
          </div>
          <div v-for="ex in idleExamples" :key="ex.id" class="idle-example-card">
            <div class="idle-example-title"><span class="idle-example-label">课件例题：</span>{{ formatIdleExampleTitle(ex) }}</div>
            <div class="idle-example-kc"><span class="idle-kc-tag">知识点：{{ formatIdleExampleKc(ex) }}</span></div>
            <pre v-if="ex.normalized_body" class="idle-example-code">{{ ex.normalized_body }}</pre>
          </div>
        </div>
      </transition>
      <transition name="slide-up">
        <div v-if="showSupplementCards && supplementCards.length" class="stuck-supplement-panel">
          <div class="stuck-supplement-header">
            <span class="stuck-supplement-title">卡住了？先做这几步</span>
            <button type="button" class="stuck-supplement-close" aria-label="关闭卡住提示" @click="dismissSupplementCards">&times;</button>
          </div>
          <p v-if="supplementIntroMessage" class="stuck-supplement-intro">{{ formatReadableLearningText(supplementIntroMessage) }}</p>
          <div v-for="(card, idx) in supplementCards" :key="card.card_type + '-' + idx" class="stuck-supplement-card">
            <div class="stuck-step-meta">
              <span class="stuck-step-index">{{ formatStepLabel(idx) }}</span>
              <span class="stuck-step-type">{{ formatSupplementCardType(card.card_type) }}</span>
            </div>
            <div class="stuck-step-title">{{ formatSupplementTitle(card) }}</div>
            <div class="stuck-step-why">{{ formatReadableLearningText(card.why_this_now) }}</div>
          </div>
        </div>
      </transition>
    </div>

    <div class="drag-handle" :class="{ dragging: isDragging }" @mousedown="startDrag"></div>

    <div id="problem-right" :style="rightContainerStyle">
      <template v-if="!isObjectiveProblem">
      <CodeEditorPanel
        ref="codeEditorPanel"
        :code="code"
        :language="language"
        :theme="theme"
        :problem="problem"
        :statusVisible="statusVisible"
        :submissionId="submissionId"
        :result="result"
        :submitting="submitting"
        :submitted="submitted"
        :debugging="debugging"
        :debugInput="debugInput"
        :debugOutput="debugOutput"
        :debugError="debugError"
        :captchaRequired="captchaRequired"
        :captchaSrc="captchaSrc"
        :captchaCode="captchaCode"
            :submissionExists="submissionExists"
            :aiTutorEnabled="isAITutorEnabledForCurrentProblem"
            :consecutiveErrors="workflowContext.consecutiveErrors"
            :can-open-ai-chat="canOpenAiChat"
            :can-request-diagnosis="canRequestDiagnosis"
            @update:code="onEditorCodeChange"
            @change-lang="onChangeLang"
            @change-theme="onChangeTheme"
        @reset-template="onResetToTemplate"
        @submit-code="submitCode"
            @debug-code="debugCode"
            @toggle-ai="toggleAIChat"
            @request-diagnosis="requestSmartDiagnosis"
            @get-captcha="getCaptchaSrc"
            @update:captchaCode="val => captchaCode = val"
            @update:debugInput="val => debugInput = val"
            @code-snapshot="handleCodeSnapshot"
      />
      </template>
      <template v-else>
        <el-card :body-style="{padding: '22px'}" class="objective-answer-card" shadow="never">
          <div class="objective-head">
            <div class="objective-head-left">
              <h3 class="objective-title">客观题作答区</h3>
              <p class="objective-subtitle">已切换为客观题模式，不展示编程编辑器与调试面板</p>
            </div>
            <el-tag :type="objectiveTagType" class="objective-type-tag">{{ objectiveTypeLabel }}</el-tag>
          </div>

          <div v-if="objectiveQuestionType === 'choice'" class="objective-body">
            <div class="objective-label">请选择一个答案</div>
            <el-radio-group v-model="objectiveChoiceAnswer" class="objective-choice-group">
              <el-radio
                v-for="opt in objectiveOptions"
                :key="opt.label"
                :value="opt.label"
                class="objective-choice-item">
                <span class="choice-label">{{ opt.label }}</span>
                <span class="choice-text">{{ opt.text }}</span>
              </el-radio>
            </el-radio-group>
          </div>

          <div v-else-if="objectiveQuestionType === 'fill_blank'" class="objective-body">
            <div class="objective-label">请填写所有空格</div>
            <div
              v-for="(blank, idx) in objectiveBlanks"
              :key="idx"
              class="objective-blank-row">
              <span class="blank-index">空{{ idx + 1 }}</span>
              <el-input
                v-model="objectiveBlankAnswers[idx]"
                placeholder="输入你的答案"
                class="objective-blank-input"/>
            </div>
          </div>

          <div v-else class="objective-body">
            <el-alert type="warning" show-icon :closable="false">当前题目元数据缺少客观题配置，请联系教师或管理员。</el-alert>
          </div>

          <div class="objective-actions">
            <el-button type="primary" :loading="objectiveSubmitting" @click="submitObjectiveAnswer">
              <el-icon><Check /></el-icon>提交答案
            </el-button>
            <el-button plain @click="resetObjectiveAnswer" style="margin-left: 10px;">
              <el-icon><Refresh /></el-icon>重置
            </el-button>
          </div>

          <div v-if="objectiveSubmissionFeedbackVisible" class="objective-feedback-card">
            <div class="ofc-header">
              <el-tag :type="objectiveSubmissionTagType" class="ofc-tag">{{ objectiveSubmissionLabel }}</el-tag>
              <span class="ofc-meta" v-if="objectiveSubmissionId">Submission: {{ objectiveSubmissionId }}</span>
            </div>
            <div class="ofc-body">
              <template v-if="objectiveSubmissionPending">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span class="ofc-text">客观题正在判分中，请稍候...</span>
              </template>
              <template v-else-if="objectiveJudgeInfo">
                <span class="ofc-text">
                  得分 {{ objectiveJudgeInfo.score }}/100
                  <template v-if="objectiveJudgeInfo.question_type === 'fill_blank'">
                    ，填空完成 {{ objectiveJudgeInfo.filled_blanks }}/{{ objectiveJudgeInfo.total_blanks }}
                  </template>
                </span>
              </template>
            </div>
            <div class="ofc-actions" v-if="objectiveSubmissionId">
              <el-button size="small" plain @click="goObjectiveSubmissionDetails">查看提交详情</el-button>
            </div>
          </div>
        </el-card>
      </template>
    </div>

    <el-dialog v-model="graphVisible">
      <div id="pieChart-detail">
        <ECharts :options="largePie" :initOptions="largePieInitOpts"></ECharts>
      </div>
      <template #footer><div>
        <el-button plain @click="graphVisible=false">{{$t('m.Close')}}</el-button>
      </div></template>
    </el-dialog>


    <!-- AST 逻辑树全屏查看器 -->
    <el-dialog v-model="astDialogVisible"
           title="AST Logic Tree"
           width="92%"
           :close-on-click-modal="true"
           class="ast-fullscreen-dialog"
           append-to-body>
      <div class="ast-viewer-container" ref="astContainer">
        <div v-if="!astTreeData" class="ast-empty-state">
          <el-icon :size="48" color="#475569"><Share /></el-icon>
          <p>No AST data available. Run analysis first.</p>
        </div>
      </div>
      <div class="ast-legend">
        <span class="ast-legend-item"><i style="background:#3B82F6"></i> Function</span>
        <span class="ast-legend-item"><i style="background:#F59E0B"></i> Condition</span>
        <span class="ast-legend-item"><i style="background:#10B981"></i> Loop</span>
        <span class="ast-legend-item"><i style="background:#8B5CF6"></i> Return</span>
        <span class="ast-legend-item"><i style="background:#EF4444"></i> Call</span>
      </div>
    </el-dialog>


    <!-- 热点悬浮提示 -->
    <div v-show="hotspotTooltip.visible" class="hotspot-floating-tooltip" :style="hotspotTooltipStyle">
      <div class="hft-header">
        <span class="hft-type-badge" :class="'hft-' + hotspotTooltip.type">{{ hotspotTooltip.typeLabel }}</span>
      </div>
      <div class="hft-text">{{ hotspotTooltip.text }}</div>
      <div class="hft-impact-row">
        <div class="hft-impact-bar"><div class="hft-impact-fill" :style="{ width: hotspotTooltip.impact + '%' }"></div></div>
        <span class="hft-impact-val">{{ hotspotTooltip.impact }}% impact</span>
      </div>
    </div>

    <!-- 统一 Agent 面板 -->
    <UnifiedAgentPanel
      v-if="isAITutorEnabledForCurrentProblem"
      ref="agentPanel"
      :visible="agentPanelVisible"
      :messages="agentMessages"
      :loading="agentLoading"
      :input-mode="agentInputMode"
      :quick-actions="quickActions"
      :checkpoints="workflowCheckpoints"
      :execution-trace="workflowContext.executionTrace || []"
      :student-code="workflowContext.lastCodeSnapshot || ''"
      :session-id="workflowContext.session_id || ''"
      :problem-id="problem.id || 0"
      :language-pack-id="problem.language_pack_id || null"
      :workflow-query-client="_workflowSessionQueryClient"
      :runtime-context="runtimeContext"
      :context-usage="contextUsage"
      :pending-human-action="pendingHumanAction"
      :student-event="charStudentEvent"
      :can-chat-input="canUseChatInput"
      :can-start-ideate="canStartIdeate"
      :can-request-skeleton="canRequestSkeleton"
      :can-request-execution-trace="canRequestExecutionTrace"
      :plan-steps="planSteps"
      :plan-paused="planPaused"
      :plan-completed="planCompleted"
      :plan-surrendered="planSurrendered"
      :plan-recommendation="planRecommendation"
      :plan-reasoning="planReasoning"
      :last-conversation-cards="lastConversationCards"
      :parsons-state="parsonsState"
      :parsons-walkthrough="parsonsWalkthrough"
      @close="agentPanelVisible = false"
      @send="handleAgentSend"
      @trigger-agent="handleTriggerAgent"
      @switch-input-mode="handleSwitchInputMode"
      @show-warmup="handleShowWarmup"
      @request-skeleton="handleAgentRequestSkeleton"
      @request-transfer="handleAgentRequestTransfer"
      @highlight-errors="handleHighlightErrors"
      @insert-code="handleInsertCode"
      @clear-highlights="handleClearHighlights"
      @navigate-problem="navigateToRecoveryProblem"
      @stop-agent="stopAgent"
      @restore-checkpoint="restoreCheckpoint"
      @regenerate="handleRegenerate"
      @clear-chat="handleClearChat"
      @report-event="handleReportEvent"
      @request-execution-trace="handleRequestExecutionTrace"
      @request-visualize="handleRequestVisualize"
      @approve-action="handleInterrupt('confirm')"
      @reject-action="handleInterrupt('reject')"
      @recover-checkpoint="handleRecoverLatestCheckpoint"
      @restart-workflow="handleClearChat"
      @accept-plan-recommendation="handleAcceptPlanRecommendation"
      @dismiss-plan-recommendation="handleDismissPlanRecommendation"
      @plan-confirm-step="onPlanConfirmStep"
      @plan-skip-step="onPlanSkipStep"
      @plan-pause="onPlanPause"
      @plan-resume="onPlanPause"
      @plan-take-over="onPlanTakeOver"
      @plan-redirect="onPlanRedirect"
      @parsons-submit="handleParsonsSubmit"
      @parsons-reset="handleParsonsReset"
      @parsons-walkthrough-submit="handleParsonsWalkthroughSubmit"
      @parsons-walkthrough-continue="handleParsonsWalkthroughContinue"
      @compact-session="handleCompactSession"
      @fork-session="handleForkSession"
    />

    <!-- Learning Twin 面板 -->
    <div v-if="isAITutorEnabledForCurrentProblem && agentPanelVisible && learningTwinVisible" class="learning-twin-wrap">
      <LearningTwinPanel
        :visible="learningTwinVisible"
        :problem-id="problem.id || 0"
        :language-pack-id="problem.language_pack_id || null"
        @close="learningTwinVisible = false"
        @action="handleLearningTwinAction"
      />
    </div>

    <!-- Agent 面板入口按钮 -->
    <el-button
      v-if="isAITutorEnabledForCurrentProblem && !agentPanelVisible"
      class="agent-panel-fab"
      type="primary"
      circle
      @click="agentPanelVisible = true"
    >
      <el-icon :size="20"><School /></el-icon>
    </el-button>

    <!-- Pre-flight 提交前拦截对话框 -->
    <PreflightDialog
      :visible="preflightDialog.visible"
      :question="preflightDialog.question"
      :hint="preflightDialog.hint"
      :highlight-reason="preflightDialog.highlightReason"
      :alert-title="preflightDialog.alertTitle"
      :line-number="preflightDialog.lineNumber"
      :code-snippet="preflightDialog.codeSnippet"
      @go-edit="handlePreflightGoEdit"
      @force-submit="handlePreflightForceSubmit"
    />


    <!-- 解题过程河流图 -->
    <el-tooltip content="查看解题过程" placement="left" v-if="showRiverButton">
      <button class="river-fab" @click="toggleRiver" aria-label="查看解题过程">
        📊
      </button>
    </el-tooltip>
    <el-dialog
      v-model="riverVisible"
      title="解题过程"
      width="820px"
      class="river-modal"
    >
      <SubmissionRiver
        v-if="riverVisible"
        :riverData="riverData"
        :loading="riverLoading"
      />
    </el-dialog>

    <!-- AC 提交成功动画覆盖层 -->
    <transition name="success-fade">
      <div v-if="showSuccessOverlay" class="success-overlay">
        <canvas ref="confettiCanvas" class="confetti-canvas"></canvas>
        <div class="success-card">
          <button
            type="button"
            class="success-dismiss-btn"
            aria-label="关闭通过弹窗"
            @click="closeSuccess"
          >
            ×
          </button>

          <div class="success-char-area" v-if="acCharacter">
            <img :src="acCharSpriteSrc" class="success-char-sprite" :alt="acCharacter.name" />
            <div class="success-char-bubble" :style="{ borderColor: acCharacter.color + '60' }">
              <span class="success-char-name" :style="{ color: acCharacter.color }">{{ acCharacter.name }}</span>
              <span class="success-char-line">{{ acCharLine }}</span>
            </div>
          </div>

          <div class="success-badge">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
          </div>
          <div class="success-title">Accepted</div>
          <div class="success-sub">恭喜！代码通过所有测试用例</div>
          <div class="success-stats-row">
            <div class="ss-item">
              <div class="ss-num" style="color: #10b981;">{{ successScore }}</div>
              <div class="ss-label">得分</div>
            </div>
            <div class="ss-item">
              <div class="ss-num">{{ successTime }}</div>
              <div class="ss-label">运行时间</div>
            </div>
            <div class="ss-item">
              <div class="ss-num">{{ successMemory }}</div>
              <div class="ss-label">内存</div>
            </div>
          </div>
          <button
            v-if="isAITutorEnabledForCurrentProblem"
            type="button"
            class="success-close-btn"
            @click="closeSuccess"
          >
            查看学习总结
          </button>
          <div class="success-secondary-actions">
            <a class="success-detail-link" @click="viewSubmissionDetails">
              查看提交详情
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
            </a>
          </div>
        </div>
      </div>
    </transition>

  </div>
</template>

<script>
  import { defineAsyncComponent } from 'vue'
  import {mapActions} from 'vuex'
  import { ElMessageBox } from 'element-plus'
  import {
    InfoFilled, DocumentCopy, ArrowDown, Share, School,
    Download, Check, Refresh, Loading
  } from '@element-plus/icons-vue'
  import storage from '@/utils/storage'
  import {JUDGE_STATUS, buildProblemCodeKey, buildProblemErrorKey} from '@/utils/constants'
  import api from '@oj/api'
  import {pie, largePie} from './chartData'
  import { sanitize } from '@/utils/sanitize'
  import { postLearningEventsKeepalive } from '@/utils/learningEventsTransport'
  import marked from 'marked'
  import hljs from '@/utils/hljs'
  import CodeEditorPanel from './CodeEditorPanel.vue'
  import { getCharacter, getSpritePath } from './characterConfig'
  import { useTutorWorkflowRuntime } from '@/composables/problem/useTutorWorkflowRuntime'
  import { useForm } from '@/composables/useForm'
  import { parseReferences } from './useReferenceParse'
  import { useFrustration } from '@/composables/problem/useFrustration'
  import { useSubmission } from '@/composables/problem/useSubmission'
  import { useAstVisualization } from '@/composables/problem/useAstVisualization'
  import { useProblemPresentation } from '@/composables/problem/useProblemPresentation'

  marked.setOptions({
    highlight: function (code, lang) {
      try {
        if (lang && hljs.getLanguage(lang)) {
          return hljs.highlight(code, {language: lang}).value
        }
        return hljs.highlightAuto(code).value
      } catch (_) {
        return code
      }
    },
    breaks: true
  })

  const filtedStatus = ['-1', '-2', '0', '1', '2', '3', '4', '8']
  const AI_TERMINOLOGY = Object.freeze({
    postAcAdversarialAnalysis: 'AI 优化（AC 后）对抗分析'
  })
  const SUPPLEMENT_CARD_TYPE_LABELS = Object.freeze({
    course_example: '课件例题',
    objective_problem: '知识点小练习',
    faded_example: '渐退示例',
    coding_problem: '编程练习',
    transfer_problem: '迁移练习'
  })
  const LEARNING_TEXT_REPLACEMENTS = Object.freeze([
    ['for循环与遍历', '循环与遍历'],
    ['for 循环与遍历', '循环与遍历'],
    ['for循环', '循环结构'],
    ['for 循环', '循环结构'],
    ['coding_problem', '编程练习'],
    ['faded_example', '渐退示例'],
    ['course_example', '课件例题'],
    ['objective_problem', '知识点小练习'],
    ['transfer_problem', '迁移练习']
  ])
  const UnifiedAgentPanel = defineAsyncComponent(() => import('./UnifiedAgentPanel.vue'))
  const LearningTwinPanel = defineAsyncComponent(() => import('@oj/components/skillProfile/LearningTwinPanel.vue'))
  const PreflightDialog = defineAsyncComponent(() => import('./PreflightDialog.vue'))
  const SubmissionRiver = defineAsyncComponent(() => import('@oj/components/SubmissionRiver'))
  const tutorWorkflowRuntime = useTutorWorkflowRuntime()

  export default {
    name: 'Problem',
    components: {
      CodeEditorPanel,
      UnifiedAgentPanel,
      LearningTwinPanel,
      PreflightDialog,
      SubmissionRiver,
      InfoFilled,
      DocumentCopy,
      ArrowDown,
      Share,
      School,
      Download,
      Check,
      Refresh,
      Loading
    },
    setup () {
      const form = useForm()
      const frustration = useFrustration()
      const submission = useSubmission()
      const astViz = useAstVisualization()
      const presentation = useProblemPresentation()
      return {
        ...form,
        ...frustration,
        ...submission,
        ...astViz,
        ...presentation
      }
    },
    data () {
      return {
        ...tutorWorkflowRuntime.data.call(this),
        hintVisible: false,

        lastEditTime: Date.now(),
        editCount: 0,
        startCodeLength: 0,
        graphVisible: false,
        problemID: '',
        code: '',
        language: '',
        theme: 'solarized',
        problem: {
          title: '',
          description: '',
          hint: '',
          my_status: '',
          template: {},
          languages: [],
          created_by: { username: '' },
          tags: [],
          io_mode: { io_mode: 'Standard IO', input: 'input.txt', output: 'output.txt' }
        },
        pie: pie,
        largePie: largePie,
        largePieInitOpts: { width: '500', height: '480' },
        leftPanelWidth: null,
        isDragging: false,
        dragStartX: 0,
        dragInitWidth: 0,
        aiTerminology: AI_TERMINOLOGY,
        idleTimer: null,
        idleExamples: [],
        showIdleExamples: false,
        parsonsState: { submitting: false, hint: '', lastResult: null },
        parsonsWalkthrough: { visible: false, loading: false, score: 0, feedback: '', lastPassed: false, canRewrite: false, attempts: 0, sessionId: '' },
        supplementCards: [],
        supplementIntroMessage: '',
        showSupplementCards: false,
        learningTwinVisible: true
      }
    },
    beforeRouteEnter (to, from, next) {
      let problemCode = storage.get(buildProblemCodeKey(to.params.problemID))
      if (problemCode) {
        next(vm => {
          vm.applySavedEditorDraft(to.params.problemID)
        })
      } else {
        next()
      }
    },
    created () {
      this._lastSubmissionStatus = null
      this._telemetryTimer = null
      this._codeDebounceTimer = null
      this._problemInitToken = 0
      this._onDrag = (e) => {
        if (!this.isDragging) return
        const delta = e.clientX - this.dragStartX
        const containerWidth = this.$el.offsetWidth - 48 - 24
        this.leftPanelWidth = Math.max(200, Math.min(containerWidth * 0.6, this.dragInitWidth + delta))
      }
      this._stopDrag = () => {
        this.isDragging = false
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        document.removeEventListener('mousemove', this._onDrag)
        document.removeEventListener('mouseup', this._stopDrag)
        this.$nextTick(() => {
          var editorRef = this.getEditorRef()
          if (editorRef && typeof editorRef.refreshEditorLayout === 'function') {
            editorRef.refreshEditorLayout()
          }
        })
      }
    },
    mounted () {
      this.init()
      this.startDwellTimer()
      this.startIdleExampleTimer()
      this.$nextTick(() => {
        this.attachCodeMirrorChangeHandler()
      })
      document.body.style.overflow = 'hidden'

      this._pageOpenTime = Date.now()
      this._pendingEvents = []
      this._hasSubmitted = false
      this._beforeUnloadHandler = () => {
        if (this.problem && this.problem._id && this.code) {
          storage.set(buildProblemCodeKey(this.problem._id), { code: this.code, language: this.language })
        }
      }
      window.addEventListener('beforeunload', this._beforeUnloadHandler)
      this._batchTimer = setInterval(() => {
        this._flushEvents()
      }, 30000)
      this._queueEvent({ event_type: 'problem_opened', problem_id: this.problem && this.problem.id })
    },
    beforeUnmount () {
      if (typeof tutorWorkflowRuntime.beforeUnmount === 'function') {
        tutorWorkflowRuntime.beforeUnmount.call(this)
      }
      document.body.style.overflow = ''
      if (this.idleTimer) {
        clearTimeout(this.idleTimer)
        this.idleTimer = null
      }
      if (this._idleResetHandler) {
        document.removeEventListener('keydown', this._idleResetHandler)
        document.removeEventListener('mousedown', this._idleResetHandler)
      }
      if (this._telemetryTimer) {
        clearTimeout(this._telemetryTimer)
        this._telemetryTimer = null
      }
      if (this._codeDebounceTimer) {
        clearTimeout(this._codeDebounceTimer)
        this._codeDebounceTimer = null
      }
      if (this._batchTimer) {
        clearInterval(this._batchTimer)
        this._batchTimer = null
      }
      this._queueEvent({
        event_type: 'problem_closed',
        problem_id: this.problem && this.problem.id,
        extra_data: {
          dwell_ms: Date.now() - (this._pageOpenTime || Date.now()),
          code_edited: (this._totalKeystrokes || 0) > 0,
          submitted: !!this._hasSubmitted
        }
      })
      this._flushEventsBeacon()
      if (this._autoSaveTimer) { clearTimeout(this._autoSaveTimer); this._autoSaveTimer = null }
      if (this._beforeUnloadHandler) {
        window.removeEventListener('beforeunload', this._beforeUnloadHandler)
        this._beforeUnloadHandler()
      }
      document.removeEventListener('mousemove', this._onDrag)
      document.removeEventListener('mouseup', this._stopDrag)
    },
    methods: {
      ...tutorWorkflowRuntime.methods,
      sanitize,
      startIdleExampleTimer () {
        const resetTimer = () => {
          if (this.idleTimer) clearTimeout(this.idleTimer)
          this.idleTimer = setTimeout(() => this.loadIdleExamples(), 5 * 60 * 1000)
        }
        resetTimer()
        this._idleResetHandler = resetTimer
        document.addEventListener('keydown', this._idleResetHandler)
        document.addEventListener('mousedown', this._idleResetHandler)
      },
      async loadIdleExamples () {
        if (!this.problem || !this.problem.id) return
        if (this.showIdleExamples) return
        if (this.$route.query.rechallenge === '1') return
        try {
          const res = await api.getRelatedExamples(this.problem.id)
          this.idleExamples = (res.data && res.data.data) || []
          if (this.idleExamples.length > 0) {
            this.showIdleExamples = true
          }
          await this.loadStuckSupplementPlan('idle')
        } catch (e) {
          console.warn('[Problem] loadIdleExamples failed:', e)
        }
      },
      dismissIdleExamples () {
        this.showIdleExamples = false
      },
      async loadStuckSupplementPlan (reason = 'stuck') {
        if (!this.problem || !this.problem.language_pack_id || !this.problem.id) {
          return
        }
        if (this.$route.query.rechallenge === '1') return
        if (reason !== 'error' && this.showSupplementCards) return
        try {
          const res = await api.getSupplementPlan({
            trigger: 'stuck',
            language_pack_id: this.problem.language_pack_id,
            problem_id: this.problem.id,
            requested_count: 3
          })
          const plan = (res.data && res.data.data) || {}
          const cards = Array.isArray(plan.cards) ? plan.cards : []
          this.supplementCards = cards
          this.supplementIntroMessage = plan.intro_message || ''
          this.showSupplementCards = cards.length > 0
        } catch (e) {
          console.warn('[Problem] loadStuckSupplementPlan failed:', e)
        }
      },
      dismissSupplementCards () {
        this.showSupplementCards = false
      },
      formatStepLabel (idx) {
        return `第 ${idx + 1} 步`
      },
      formatSupplementCardType (cardType) {
        return SUPPLEMENT_CARD_TYPE_LABELS[cardType] || '学习卡片'
      },
      formatReadableLearningText (value) {
        if (value === null || typeof value === 'undefined') return ''
        return LEARNING_TEXT_REPLACEMENTS.reduce((text, pair) => {
          return text.split(pair[0]).join(pair[1])
        }, String(value))
      },
      formatSupplementTitle (card) {
        const title = card && (card.title || (card.payload && card.payload.title))
        return this.formatReadableLearningText(title || '练习卡片')
      },
      formatIdleExampleTitle (example) {
        return this.formatReadableLearningText((example && example.source_title) || '课件例题')
      },
      formatIdleExampleKc (example) {
        return this.formatReadableLearningText((example && example.kc_name) || '相关知识点')
      },
      renderMarkdown (text) {
        if (!text) return ''
        // sup 上标替换放在 sanitize 之前，避免 sanitize 后再次拼 HTML（见 BUG #26）。
        const withSup = String(text).replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
        return sanitize(marked(withSup))
      },
      _queueEvent (ev) {
        if (!this._pendingEvents) this._pendingEvents = []
        ev.client_timestamp = new Date().toISOString()
        if (!ev.problem_id && this.problem) ev.problem_id = this.problem.id
        this._pendingEvents.push(ev)
      },
      _flushEvents () {
        if (!this._pendingEvents || !this._pendingEvents.length) return
        const batch = this._pendingEvents.splice(0)
        api.submitLearningEventsBatch(batch).catch(() => {})
      },
      _flushEventsBeacon () {
        if (!this._pendingEvents || !this._pendingEvents.length) return
        const batch = this._pendingEvents.splice(0)
        postLearningEventsKeepalive(batch, (failedBatch) => {
          return api.submitLearningEventsBatch(failedBatch).catch(() => {})
        }).catch(() => {
          api.submitLearningEventsBatch(batch).catch(() => {})
        })
      },
      toggleAIChat () {
        if (!this.isAITutorEnabledForCurrentProblem) {
          this.$info('该临时题已关闭 AI 导学')
          return
        }
        this.agentPanelVisible = true
        this.callAgent(3, {
          code: this.code,
          problem_id: this.problem.id
        }).catch(() => {})
      },
      requestSmartDiagnosis () {
        if (!this.canRequestDiagnosis) {
          return
        }
        this.callAgent(4, { submission_id: this.submissionId }).catch(() => {})
      },
      handleCodeSnapshot (data) {
        api.submitCodeSnapshot(data).catch(() => {})
      },
      handleReportEvent (ev) {
        this._queueEvent(ev)
      },
      kcColor (kc) {
        if (kc.mastery == null) return 'blue'
        if (kc.mastery < 0.3) return 'red'
        if (kc.mastery < 0.7) return 'yellow'
        return 'green'
      },
      kcElType (kc) {
        const c = this.kcColor(kc)
        if (c === 'red') return 'danger'
        if (c === 'yellow') return 'warning'
        if (c === 'green') return 'success'
        return 'primary'
      },
      normalizeSampleText (value) {
        if (value === null || typeof value === 'undefined') return ''
        return String(value)
          .replace(/\\r\\n/g, '\n')
          .replace(/\\n/g, '\n')
          .replace(/\\t/g, '\t')
      },
      pickDefaultLanguage (languages) {
        if (!Array.isArray(languages) || languages.length === 0) {
          return this.language
        }
        const packLang = this.problem && this.problem.language_pack_primary_language
        if (packLang && languages.includes(packLang)) {
          return packLang
        }
        if (this.language && languages.includes(this.language)) {
          return this.language
        }
        return languages[0]
      },
      getSavedProblemDraft (problemID) {
        const draft = storage.get(buildProblemCodeKey(problemID))
        if (!draft) {
          return null
        }
        return {
          language: typeof draft.language === 'string' ? draft.language : this.language,
          code: typeof draft.code === 'string' ? draft.code : ''
        }
      },
      applySavedEditorDraft (problemID) {
        const draft = this.getSavedProblemDraft(problemID)
        if (!draft) {
          return false
        }
        this.language = draft.language || this.language
        this.setEditorDocument(draft.code, {
          silent: true,
          cursor: { line: 0, ch: 0 },
          scroll: { left: 0, top: 0 }
        })
        return true
      },
      onEditorCodeChange (nextCode) {
        this.code = typeof nextCode === 'string' ? nextCode : ''
        if (this._autoSaveTimer) clearTimeout(this._autoSaveTimer)
        this._autoSaveTimer = setTimeout(() => {
          if (this.problem && this.problem._id) {
            storage.set(buildProblemCodeKey(this.problem._id), { code: this.code, language: this.language })
          }
        }, 1000)
      },
      setEditorDocument (nextCode, config = {}) {
        const normalizedCode = typeof nextCode === 'string' ? nextCode : ''
        this.code = normalizedCode
        const editorRef = this.getEditorRef()
        if (editorRef && typeof editorRef.setDocument === 'function') {
          editorRef.setDocument(normalizedCode, config)
          return
        }
        this.$nextTick(() => {
          const ref = this.getEditorRef()
          if (ref && typeof ref.setDocument === 'function') {
            ref.setDocument(normalizedCode, config)
          }
        })
      },
      ...mapActions(['changeDomTitle']),
      scheduleAfterFirstPaint (fn) {
        if (typeof fn !== 'function') return
        const run = () => {
          setTimeout(fn, 0)
        }
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
          window.requestAnimationFrame(run)
          return
        }
        run()
      },
      scheduleNonCriticalProblemHydration (problem, requestToken) {
        if (!problem || !problem.id) return
        this.scheduleAfterFirstPaint(() => {
          if (requestToken !== this._problemInitToken) return
          api.submissionExists(problem.id).then(res => {
            if (requestToken !== this._problemInitToken) return
            this.submissionExists = res.data.data
          }).catch(() => {})
          if (this.isAITutorEnabledForCurrentProblem) {
            this.initWorkflowSession(problem.id)
          }
        })
      },
      init () {
        this.$loadingStart()
        const requestToken = ++this._problemInitToken
        this.problemID = this.$route.params.problemID
        const hasSavedDraft = this.applySavedEditorDraft(this.problemID)
        if (!hasSavedDraft) {
          this.setEditorDocument('', {
            silent: true,
            cursor: { line: 0, ch: 0 },
            scroll: { left: 0, top: 0 }
          })
        }

        let savedErrors = storage.get(buildProblemErrorKey(this.problemID))
        if (typeof savedErrors === 'number' && savedErrors > 0) {
          this.workflowContext.consecutiveErrors = savedErrors
        } else {
          this.workflowContext.consecutiveErrors = 0
        }

        api.getProblem(this.problemID, { with_kcs: true }).then(res => {
          if (requestToken !== this._problemInitToken) return
          let problem = res.data.data
          if (!problem.io_mode) {
            problem.io_mode = { io_mode: 'Standard IO', input: 'input.txt', output: 'output.txt' }
          }
          if (!problem.created_by) {
            problem.created_by = { username: '' }
          }
          this.changeDomTitle({title: problem.title})
          problem.languages = Array.isArray(problem.languages) ? problem.languages : []
          this.problem = problem
          this.supplementCards = []
          this.supplementIntroMessage = ''
          this.showSupplementCards = false
          if (!this.isAITutorEnabledForCurrentProblem) {
            this.agentPanelVisible = false
          }
          this.workflowContext.problem_id = problem.id
          this.resetObjectiveAnswer()
          this.maybeDispatchParsonsFromRoute()
          if (problem.statistic_info) {
            this.changePie(problem)
          }

          if (!hasSavedDraft) {
            this.language = this.pickDefaultLanguage(this.problem.languages)
            let template = this.problem.template
            const templateCode = template && template[this.language] ? template[this.language] : ''
            this.setEditorDocument(templateCode, {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
          }
          this.startCodeLength = this.code.length
          this.$loadingFinish()
          this.scheduleNonCriticalProblemHydration(problem, requestToken)
        }, () => {
          if (requestToken !== this._problemInitToken) return
          this.$loadingFinish()
        })
      },

      getEditorRef () {
        if (this.$refs && this.$refs.editor) return this.$refs.editor
        var panel = this.$refs && this.$refs.codeEditorPanel
        if (panel && panel.$refs && panel.$refs.editor) {
          return panel.$refs.editor
        }
        return null
      },

      moveCursorToDocumentEnd (doc) {
        if (!doc) return
        var totalLines = doc.lineCount()
        if (!Number.isInteger(totalLines) || totalLines <= 0) {
          return
        }
        var lastLine = totalLines - 1
        var lineText = doc.getLine(lastLine)
        var endCh = typeof lineText === 'string' ? lineText.length : 0
        doc.setCursor(lastLine, endCh)
      },

      buildAppendedEditorDocument (currentCode, skeletonText) {
        var existingCode = typeof currentCode === 'string' ? currentCode : ''
        var nextSkeleton = typeof skeletonText === 'string' ? skeletonText : ''
        if (!nextSkeleton) {
          return existingCode
        }
        var separator = existingCode.trim() ? '\n\n' : ''
        var trailingNewline = /\n$/.test(nextSkeleton) ? '' : '\n'
        return existingCode + separator + nextSkeleton + trailingNewline
      },

      insertSkeletonToEditor (skeletonText) {
        if (typeof skeletonText !== 'string' || !skeletonText) {
          this.$error('骨架代码为空，无法插入编辑器')
          return
        }
        var editorRef = this.getEditorRef()
        if (editorRef && typeof editorRef.appendCode === 'function') {
          editorRef.appendCode(skeletonText)
          this.$success('骨架已插入编辑器，TODO 的部分等你来填')
          return
        }
        this.$nextTick(() => {
          var nextEditorRef = this.getEditorRef()
          if (nextEditorRef && typeof nextEditorRef.appendCode === 'function') {
            nextEditorRef.appendCode(skeletonText)
            this.$success('骨架已插入编辑器，TODO 的部分等你来填')
            return
          }
          this.setEditorDocument(this.buildAppendedEditorDocument(this.code, skeletonText))
          this.$nextTick(() => {
            var fallbackEditorRef = this.getEditorRef()
            if (fallbackEditorRef && typeof fallbackEditorRef.focus === 'function') {
              fallbackEditorRef.focus()
            }
          })
          this.$success('骨架已插入编辑器，TODO 的部分等你来填')
        })
      },

      changePie (problemData) {
        for (let k in problemData.statistic_info) {
          if (filtedStatus.indexOf(k) === -1) {
            delete problemData.statistic_info[k]
          }
        }
        let acNum = problemData.accepted_number
        let data = [
          {name: 'WA', value: problemData.submission_number - acNum},
          {name: 'AC', value: acNum}
        ]
        this.pie.series[0].data = data
        let data2 = JSON.parse(JSON.stringify(data))
        data2[1].selected = true
        this.largePie.series[1].data = data2
        let legend = Object.keys(problemData.statistic_info).map(ele => JUDGE_STATUS[ele].short)
        if (legend.length === 0) {
          legend.push('AC', 'WA')
        }
        this.largePie.legend.data = legend
        let acCount = problemData.statistic_info['0']
        delete problemData.statistic_info['0']
        let largePieData = []
        Object.keys(problemData.statistic_info).forEach(ele => {
          largePieData.push({name: JUDGE_STATUS[ele].short, value: problemData.statistic_info[ele]})
        })
        largePieData.push({name: 'AC', value: acCount})
        this.largePie.series[0].data = largePieData
      },
      handleRoute (route) {
        this.$router.push(route)
      },
      hasAiTutorConversationContent () {
        return Array.isArray(this.agentMessages) && this.agentMessages.length > 0
      },
      applyEditorLanguageChange (newLang) {
        if (this.problem.template[newLang]) {
          if (this.code.trim() === '') {
            this.setEditorDocument(this.problem.template[newLang], {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
          }
        }
        this.language = newLang
      },
      async clearAiConversationForLanguageSwitch () {
        if (!this.isAITutorEnabledForCurrentProblem) {
          return
        }
        if (!this.workflowContext || !this.workflowContext.problem_id) {
          return
        }
        await this.clearWorkflow()
      },
      async commitLanguageSwitch (newLang, options = {}) {
        const clearAiConversation = !!options.clearAiConversation
        if (clearAiConversation) {
          try {
            await this.clearAiConversationForLanguageSwitch()
          } catch (err) {
            console.error('[workflow] clear before language switch failed', err)
            this.$error('语言切换失败：清空 AI 导学对话失败')
            return
          }
        }
        this.applyEditorLanguageChange(newLang)
      },
      onChangeLang (newLang) {
        if (!newLang || newLang === this.language) {
          return
        }
        const shouldClearAiConversation = this.isAITutorEnabledForCurrentProblem
        if (!shouldClearAiConversation) {
          this.applyEditorLanguageChange(newLang)
          return
        }
        if (this.hasAiTutorConversationContent()) {
          ElMessageBox.confirm('切换后将清空当前 AI 导学对话记录，是否继续？', '切换编程语言', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(async () => {
            await this.commitLanguageSwitch(newLang, { clearAiConversation: true })
          }).catch(() => {})
          return
        }
        this.commitLanguageSwitch(newLang, { clearAiConversation: true })
      },
      onChangeTheme (newTheme) {
        this.theme = newTheme
      },
      onResetToTemplate () {
        ElMessageBox.confirm(this.$t('m.Are_you_sure_you_want_to_reset_your_code'), this.$t('m.Hint') || '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          let template = this.problem.template
          if (template && template[this.language]) {
            this.setEditorDocument(template[this.language], {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
          } else {
            this.setEditorDocument('', {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
          }
        }).catch(() => {})
      },
      onCopy (event) {
        this.$success('Code copied')
      },
      onCopyError (e) {
        this.$error('Failed to copy code')
      },
      apSeverityColor (severity) {
        return {
          critical: 'error',
          high: 'warning',
          medium: 'gold',
          low: 'primary'
        }[severity] || 'default'
      },
      handleHighlightErrors (errors) {
        var editorRef = this.getEditorRef()
        if (editorRef && editorRef.highlightErrorLines) {
          editorRef.highlightErrorLines(errors)
        }
      },
      handleInsertCode ({ code, position }) {
        if (position === 'append') {
          this.insertSkeletonToEditor(code)
          return
        }
        var applyInsertion = () => {
          var editorRef = this.getEditorRef()
          if (!editorRef) return false
          if (position === 'cursor' && typeof editorRef.insertCodeAtCursor === 'function') {
            editorRef.insertCodeAtCursor(code)
            return true
          }
          if (position && position.startLine && typeof editorRef.replaceLines === 'function') {
            editorRef.replaceLines(position.startLine, position.endLine, code)
            return true
          }
          return false
        }
        if (applyInsertion()) {
          this.$success('代码已填入编辑器')
          return
        }
        this.$nextTick(() => {
          if (applyInsertion()) {
            this.$success('代码已填入编辑器')
            return
          }
          this.$error('编辑器尚未就绪，请稍后重试')
        })
      },
      handleClearHighlights () {
        var editorRef = this.getEditorRef()
        if (editorRef && editorRef.clearErrorHighlights) {
          editorRef.clearErrorHighlights()
        }
      },
      resolveExecutionTraceWorkflowEvent () {
        if (this.workflowContext.current_state === 'ERROR_FEEDBACK') {
          return 'ERROR_FEEDBACK'
        }
        return 'CODING'
      },
      handleRequestExecutionTrace ({ source } = {}) {
        const phase = this.resolveExecutionTraceWorkflowEvent()
        this.dispatchWorkflowEvent(phase, {
          problem_id: this.problem.id,
          code: this.code,
          submission_id: this.submissionId || this.workflowContext.submissionId,
          request_execution_trace: true
        }).catch(() => {
          this.pushAgentMessage({
            type: 'system',
            content: source === 'error_diagnosis' ? '运行轨迹生成失败，请稍后重试' : '当前无法生成代码运行轨迹，请稍后重试'
          })
        })
      },
      resolveInlineVisualizeIntent (text = '') {
        const normalized = String(text || '').toLowerCase()
        if (normalized.includes('复杂度')) return 'complexity_compare'
        if (normalized.includes('递归')) return 'recursion_stack'
        if (normalized.includes('内存') || normalized.includes('引用')) return 'memory_layout'
        if (normalized.includes('数据流') || normalized.includes('调用链')) return 'data_flow'
        if (normalized.includes('掌握') || normalized.includes('雷达')) return 'kc_mastery_radar'
        if (normalized.includes('链表') || normalized.includes('树') || normalized.includes('队列') || normalized.includes('栈')) return 'data_structure_state'
        if (normalized.includes('循环') || normalized.includes('range')) return 'for_loop_trace'
        if (this.workflowContext.current_state === 'AC_REVIEW') return 'complexity_compare'
        return 'flowchart'
      },
      handleRequestVisualize () {
        if (!this.canUseChatInput) {
          return
        }
        ElMessageBox.prompt(
          '你想把哪个“执行过程/概念”画出来？',
          '教学可视化',
          {
            confirmButtonText: '开始画图',
            cancelButtonText: '取消',
            inputPlaceholder: '例如：画一下 range(5) 的迭代过程',
            inputPattern: /\S+/,
            inputErrorMessage: '请输入可视化描述'
          }
        ).then(({ value }) => {
          const promptText = String(value || '').trim()
          if (!promptText) return
          const intent = this.resolveInlineVisualizeIntent(promptText)
          this.dispatchWorkflowEvent('VISUALIZE', {
            problem_id: this.problem.id,
            intent,
            prompt: promptText,
            context_hints: {
              phase: this.workflowContext.current_state,
              language: this.language,
              submission_id: this.submissionId || this.workflowContext.submissionId || '',
              code_preview: (this.code || '').slice(0, 800)
            },
            source_role: 'Student'
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '可视化生成失败，请稍后重试' })
          })
        }).catch(() => {})
      },
      handleAgentSend ({ text, mode }) {
        if (mode === 'ideate' && !this.canStartIdeate) {
          return
        }
        if (mode !== 'ideate' && !this.canUseChatInput) {
          return
        }
        this.pushAgentMessage({ type: 'user', content: text })
        if (mode === 'ideate') {
          this.agentInputMode = 'chat'
          this.callAgent(2, {
            thought_text: text,
            problem_id: this.problem.id
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '思路分析请求失败，请稍后重试' })
          })
        } else {
          // 把 @card/@last_xxx 引用和当前 Mode 传给 tutor-graph chat 节点
          const references = parseReferences(text)
          this.dispatchWorkflowEvent('CHAT', {
            message: text,
            code: this.code,
            language: this.language,
            submission_id: this.submissionId || this.workflowContext.submissionId,
            problem_id: this.problem.id,
            references,
            mode: this.activeConversationMode || 'chat'
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '对话请求失败，请稍后重试' })
          })
        }
      },
      handleTriggerAgent (action) {
        const key = action.key
        const event = action.event
        const welcomePayload = action.payload || {}
        if (key === 'problem_guide' || key === 're_read') {
          this.dispatchWorkflowEvent(event || 'READING', { problem_id: this.problem.id }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '审题引导请求失败' })
          })
        } else if (key === 're_ideate') {
          this.agentInputMode = 'ideate'
          this.pushAgentMessage({ type: 'system', content: '请重新描述你的解题思路，我来帮你梳理。' })
        } else if (key === 'error_chain') {
          const submissionId = welcomePayload.submission_id
            || this.submissionId
            || this.workflowContext.submissionId
          this.dispatchWorkflowEvent(event || 'ERROR_FEEDBACK', { submission_id: submissionId }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '错误诊断请求失败' })
          })
        } else if (key === 'ac_review') {
          this.dispatchWorkflowEvent(event || 'AC_REVIEW', {
            submission_id: this.submissionId || this.workflowContext.submissionId,
            code: this.code,
            language: this.language,
            problem_id: this.problem.id,
            guidance_level: 1
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: 'AC 复盘请求失败' })
          })
        } else if (key === 'transfer') {
          this.handleAgentRequestTransfer()
        } else if (key === 'coding') {
          this.dispatchWorkflowEvent(event || 'CODING', {
            problem_id: this.problem.id,
            code: this.code
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '进入下一步失败，请稍后重试' })
          })
        } else if (key === 'knowledge_review') {
          this.dispatchWorkflowEvent(event || 'KNOWLEDGE_REVIEW', {
            problem_id: this.problem.id
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '知识点回顾请求失败' })
          })
        } else if (key === 'skeleton') {
          this.handleAgentRequestSkeleton()
        } else if (key === 'visualize') {
          this.handleRequestVisualize()
        } else if (key === 'parsons') {
          this.dispatchWorkflowEvent(event || 'PARSONS', {
            problem_id: this.problem.id
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '拼装挑战派发失败，请稍后重试' })
          })
        }
      },
      handleParsonsSubmit ({ sessionId, order }) {
        if (!sessionId) {
          this.pushAgentMessage({ type: 'system', content: 'Parsons 会话已失效，请重新派发' })
          return
        }
        this.parsonsState = { ...this.parsonsState, submitting: true, hint: '' }
        api.parsonsSubmit({ parsons_session_id: sessionId, ordered_block_ids: order })
          .then(res => {
            const data = (res && res.data && res.data.data) || res.data || {}
            const currentLevel = typeof data.current_fading_level === 'number'
              ? data.current_fading_level
              : null
            const nextLevel = typeof data.next_fading_level === 'number'
              ? data.next_fading_level
              : null
            this.parsonsState = {
              submitting: false,
              hint: data.hint || '',
              lastResult: {
                passed: !!data.passed,
                attempts: data.attempts || 0,
                judgeStatus: data.judge_status || '',
                cascadeDegrade: !!data.cascade_degrade,
                cascadeFailfast: !!data.cascade_failfast,
                fadingLevel: currentLevel,
                nextFadingLevel: nextLevel,
                misplacedBlockId: data.misplaced_block_id || ''
              }
            }
            if (data.passed && data.walkthrough_required) {
              this.parsonsWalkthrough = {
                visible: true,
                loading: false,
                score: 0,
                feedback: '',
                lastPassed: false,
                canRewrite: false,
                attempts: 0,
                sessionId
              }
            } else if (data.cascade_failfast) {
              this.pushAgentMessage({ type: 'system', content: 'Parsons 已多次失败，回到错误诊断主链路。' })
            } else if (data.cascade_degrade && nextLevel !== null) {
              this.dispatchWorkflowEvent('PARSONS', {
                problem_id: this.problem.id,
                previous_session_id: sessionId,
                override_fading_level: nextLevel
              }).catch(() => {})
            }
          })
          .catch(() => {
            this.parsonsState = { ...this.parsonsState, submitting: false }
            this.pushAgentMessage({ type: 'system', content: 'Parsons 提交失败，请稍后重试' })
          })
      },
      handleParsonsReset ({ sessionId }) {
        this.parsonsState = { submitting: false, hint: '', lastResult: null }
        if (!sessionId) return
        this.dispatchWorkflowEvent('PARSONS', {
          problem_id: this.problem.id,
          previous_session_id: sessionId
        }).catch(() => {
          this.pushAgentMessage({ type: 'system', content: '重新派发失败，请稍后重试' })
        })
      },
      handleParsonsWalkthroughSubmit ({ text }) {
        const sessionId = this.parsonsWalkthrough.sessionId
        if (!sessionId) return
        this.parsonsWalkthrough = { ...this.parsonsWalkthrough, loading: true }
        api.parsonsWalkthrough({ parsons_session_id: sessionId, text })
          .then(res => {
            const data = (res && res.data && res.data.data) || res.data || {}
            const nextAttempts = (this.parsonsWalkthrough.attempts || 0) + 1
            this.parsonsWalkthrough = {
              ...this.parsonsWalkthrough,
              loading: false,
              score: data.score || 0,
              feedback: data.feedback || '',
              lastPassed: !!data.passed,
              canRewrite: !!data.can_rewrite,
              attempts: nextAttempts,
              visible: true
            }
            if (data.passed) {
              this.pushAgentMessage({ type: 'system', content: '已记入顿悟笔记，做得好！' })
            } else if (!data.can_rewrite) {
              this.pushAgentMessage({ type: 'system', content: '理解还不够稳，建议下次再练一道相似题。' })
            }
          })
          .catch(() => {
            this.parsonsWalkthrough = { ...this.parsonsWalkthrough, loading: false }
            this.pushAgentMessage({ type: 'system', content: 'Walkthrough 评估失败，请稍后重试' })
          })
      },
      handleParsonsWalkthroughContinue () {
        this.parsonsWalkthrough = {
          visible: false,
          loading: false,
          score: 0,
          feedback: '',
          lastPassed: false,
          canRewrite: false,
          attempts: 0,
          sessionId: ''
        }
      },
      maybeDispatchParsonsFromRoute () {
        const q = (this.$route && this.$route.query) || {}
        if (String(q.parsons) !== '1') return
        if (!this.isAITutorEnabledForCurrentProblem) return
        this.agentPanelVisible = true
        const fsrsOrigin = q.fsrs_origin ? String(q.fsrs_origin) : ''
        this.$nextTick(() => {
          this.dispatchWorkflowEvent('PARSONS', {
            problem_id: this.problem.id,
            fsrs_origin: fsrsOrigin
          }).catch(() => {
            this.pushAgentMessage({ type: 'system', content: '拼装挑战派发失败，请稍后重试' })
          })
        })
      },
      handleSwitchInputMode (mode) {
        this.agentInputMode = mode
      },
      handleShowWarmup (question) {
        if (!this.canStartIdeate) {
          return
        }
        this.agentInputMode = 'ideate'
        this.pushAgentMessage({ type: 'ai_reply', content: question })
      },
      handleAgentRequestSkeleton () {
        if (!this.canRequestSkeleton) {
          return
        }
        for (let i = this.agentMessages.length - 1; i >= 0; i--) {
          const m = this.agentMessages[i]
          if (m.type === 'skeleton_code') {
            this.agentMessages.splice(i, 1)
          }
        }
        this.dispatchWorkflowEvent('SKELETON', {
          problem_id: this.problem.id,
          language: this.language
        }).catch(() => {
          this.pushAgentMessage({ type: 'system', content: '骨架代码生成失败' })
        })
      },
      handleAgentRequestTransfer () {
        if (!this.canRequestTransfer) {
          return
        }
        this.dispatchWorkflowEvent('TRANSFER', {
          problem_id: this.problem.id,
          code: this.code,
          submission_id: this.submissionId || this.workflowContext.submissionId
        }).catch(() => {
          this.pushAgentMessage({ type: 'system', content: '类似题生成失败' })
        })
      },
      handleRegenerate ({ messageId }) {
        this.regenerateFromMessage(messageId)
      },
      handleClearChat () {
        this.clearWorkflow().catch(err => {
          console.error('[workflow] clear failed', err)
          this.$error('清空对话失败，请重试')
        })
      },
      handleCompactSession () {
        if (!this.workflowSessionId) return
        api.tutorWorkflowCompactSession(this.workflowSessionId)
          .then(() => {
            this.$success('上下文已压缩')
            this.loadConversation()
          })
          .catch(err => {
            console.error('[workflow] compact failed', err)
            this.$error('压缩失败，请重试')
          })
      },
      handleForkSession () {
        if (!this.workflowSessionId) return
        api.tutorWorkflowForkSession(this.workflowSessionId, {})
          .then(res => {
            const data = res.data || res
            const newSessionId = data.session_id
            if (newSessionId) {
              this.$success('会话已分叉')
              this.$router.push({ name: 'problem-detail', params: { problemID: this.problem.id }, query: { session: newSessionId } })
            }
          })
          .catch(err => {
            console.error('[workflow] fork failed', err)
            this.$error('分叉失败，请重试')
          })
      },
      handleLearningTwinAction (actionLabel) {
        this.agentPanelVisible = true
        this.$nextTick(() => {
          if (this.$refs.agentPanel && this.$refs.agentPanel.handleQuickAction) {
            this.$refs.agentPanel.handleQuickAction({ key: 'twin_action', label: actionLabel, type: 'CHAT', content: actionLabel })
          }
        })
      },
      handleRecoverLatestCheckpoint () {
        if (!this.workflowCheckpoints || !this.workflowCheckpoints.length) return
        const latest = this.workflowCheckpoints[this.workflowCheckpoints.length - 1]
        this.restoreCheckpoint(latest.checkpoint_id)
      },
      startDrag (e) {
        e.preventDefault()
        this.isDragging = true
        this.dragStartX = e.clientX
        this.dragInitWidth = this.$el.querySelector('#problem-left').offsetWidth
        document.body.style.cursor = 'col-resize'
        document.body.style.userSelect = 'none'
        document.addEventListener('mousemove', this._onDrag)
        document.addEventListener('mouseup', this._stopDrag)
      }
    },
    computed: {
      ...tutorWorkflowRuntime.computed,
      submissionRoute () {
        return {name: 'submission-list', query: {problemID: this.problemID}}
      },
      leftPanelStyle () {
        if (this.leftPanelWidth !== null) {
          return { width: this.leftPanelWidth + 'px', flex: 'none' }
        }
        return {}
      },
      successScore () {
        if (!this.successResult) return 100
        const stat = this.successResult.statistic_info || {}
        return typeof stat.score === 'number' ? stat.score : 100
      },
      successTime () {
        if (!this.successResult) return '—'
        const stat = this.successResult.statistic_info || {}
        const time = stat.time_cost
        return time != null ? time + 'ms' : '—'
      },
      successMemory () {
        if (!this.successResult) return '—'
        const stat = this.successResult.statistic_info || {}
        const mem = stat.memory_cost
        if (mem == null) return '—'
        if (mem > 1024 * 1024) return (mem / 1024 / 1024).toFixed(1) + 'MB'
        if (mem > 1024) return (mem / 1024).toFixed(0) + 'KB'
        return mem + 'B'
      },
      showRiverButton () {
        return this.submissionExists || (this.problem && this.problem.my_status === 0)
      },
      isTemporaryPrivateProblem () {
        if (!this.problem || typeof this.problem !== 'object') return false
        return !!this.problem.is_ai_generated && this.problem.visibility_status === 'student_private'
      },
      acCharacter () {
        const ids = ['nene', 'yoshino', 'ayase', 'kanna', 'murasame']
        const pick = ids[Math.floor((this.problem && this.problem.id || 0) % ids.length)]
        return getCharacter(pick)
      },
      acCharSpriteSrc () {
        if (!this.acCharacter) return ''
        const exprMap = { nene: 'smile', yoshino: 'rare_gentle', ayase: 'grin', kanna: 'warm_smile', murasame: 'impressed' }
        return getSpritePath(this.acCharacter.id, exprMap[this.acCharacter.id] || 'smile')
      },
      acCharLine () {
        if (!this.acCharacter) return ''
        const lines = {
          nene: ['太棒了！通过啦～', '我就知道你可以的！', '好厉害！继续加油哦～'],
          yoshino: ['……还行。代码写得不错', '通过了。我承认你有在进步', '合格。比上次好多了'],
          ayase: ['耶！AC了！好厉害！', '什么！你通过了？不会比我快吧！', '好啊！下一道继续比！'],
          kanna: ['……通过了。很好', '……嗯。做得不错', '……逻辑正确'],
          murasame: ['切，算你行', '这种程度的题……还行吧', '不错。有点我当年的影子']
        }
        const pool = lines[this.acCharacter.id] || lines.nene
        return pool[Math.floor(Math.random() * pool.length)]
      },
      charStudentEvent () {
        if (this.submitting) return 'student_submit'
        const r = this.result && this.result.result
        if (r === 0) return 'student_ac'
        if (r === -1) return 'student_wa'
        if (r === -2) return 'student_tle'
        if (r === 4) return 'student_re'
        if (r === 1) return 'student_ce'
        return ''
      },
      isAITutorEnabledForCurrentProblem () {
        const backend = this.problem && this.problem.ai_tutor_enabled
        const backendEnabled = typeof backend === 'boolean' ? backend : !this.isTemporaryPrivateProblem
        return backendEnabled && this.isAITutorAvailableInAssignment
      },
      anySidebarOpen () {
        return this.isAITutorEnabledForCurrentProblem && this.agentPanelVisible
      },
      rightContainerStyle () {
        return {
          marginRight: this.anySidebarOpen ? '420px' : '0',
          transition: 'margin-right 0.4s cubic-bezier(0.4, 0, 0.2, 1)'
        }
      },
      hotspotTooltipStyle () {
        return {
          left: this.hotspotTooltip.x + 'px',
          top: this.hotspotTooltip.y + 'px'
        }
      },
      objectivePayload () {
        const stat = this.problem && this.problem.statistic_info
        const payload = stat && stat.objective_question
        return payload && typeof payload === 'object' ? payload : null
      },
      objectiveOptions () {
        const options = this.objectivePayload && this.objectivePayload.options
        return Array.isArray(options) ? options : []
      },
      objectiveBlanks () {
        const blanks = this.objectivePayload && this.objectivePayload.blanks
        return Array.isArray(blanks) ? blanks : ['']
      },
      objectiveTypeLabel () {
        if (this.objectiveQuestionType === 'choice') return '选择题'
        if (this.objectiveQuestionType === 'fill_blank') return '填空题'
        return '客观题'
      },
      objectiveTagColor () {
        if (this.objectiveQuestionType === 'choice') return 'blue'
        if (this.objectiveQuestionType === 'fill_blank') return 'green'
        return 'default'
      },
      objectiveTagType () {
        if (this.objectiveQuestionType === 'choice') return ''
        if (this.objectiveQuestionType === 'fill_blank') return 'success'
        return 'info'
      },
      hasDatasetDownload () {
        const stat = this.problem && this.problem.statistic_info
        if (!stat || typeof stat !== 'object') return false
        const cfg = stat.dataset_config
        if (!cfg || typeof cfg !== 'object') return false
        return !!cfg.dataset_path
      },
      objectiveSubmissionPending () {
        return this.objectiveSubmitting || this.result.result === 6 || this.result.result === 7 || this.result.result === 9
      },
      objectiveSubmissionFeedbackVisible () {
        return this.isObjectiveProblem && !!(this.objectiveSubmissionId || this.objectiveSubmitting || this.objectiveJudgeInfo)
      },
      objectiveSubmissionLabel () {
        if (this.objectiveSubmissionPending) return '判分中'
        if (this.result.result === 0) return 'AC'
        if (this.result.result === -1) return 'WA'
        return '已提交'
      },
      objectiveSubmissionTagColor () {
        if (this.objectiveSubmissionPending) return 'warning'
        if (this.result.result === 0) return 'success'
        if (this.result.result === -1) return 'error'
        return 'default'
      },
      objectiveSubmissionTagType () {
        if (this.objectiveSubmissionPending) return 'warning'
        if (this.result.result === 0) return 'success'
        if (this.result.result === -1) return 'danger'
        return 'info'
      },
      assignmentContext () {
        const q = this.$route && this.$route.query ? this.$route.query : {}
        return {
          assignmentId: q.assignment_id || '',
          classroomId: q.classroom_id || ''
        }
      },
      assignmentAITutorAllowed () {
        const q = this.$route && this.$route.query ? this.$route.query : {}
        if (typeof q.ai_tutor_allowed === 'undefined') return true
        const v = String(q.ai_tutor_allowed).toLowerCase()
        return v === '1' || v === 'true' || v === 'yes'
      },
      assignmentAITutorReason () {
        const q = this.$route && this.$route.query ? this.$route.query : {}
        return q.ai_tutor_reason || ''
      },
      isAITutorAvailableInAssignment () {
        return this.assignmentAITutorAllowed
      },
      aiTutorAvailabilityText () {
        if (this.isAITutorAvailableInAssignment) return 'AI 导学可用'
        const map = {
          disabled_by_teacher: '当前作业已被教师设置为不可使用 AI 导学',
          assignment_ended: '作业已截止，AI 导学已关闭',
          already_ac: '该题已 AC，AI 导学已自动关闭',
          review_mode: '专项复习模式下不可使用 AI 导学'
        }
        return map[this.assignmentAITutorReason] || '当前作业阶段不可使用 AI 导学'
      },
      isWorkflowRuntimeInputBlocked () {
        const runtimeState = this.runtimeContext && this.runtimeContext.runtimeState
        return runtimeState === 'WAITING_HUMAN_APPROVAL' || runtimeState === 'RESTORING'
      },
      canOpenAiChat () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canRequestDiagnosis () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canUseChatInput () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canStartIdeate () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canRequestSkeleton () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canRequestExecutionTrace () {
        return !this.isWorkflowRuntimeInputBlocked
      },
      canRequestTransfer () {
        return !this.isWorkflowRuntimeInputBlocked
      }
    },
    beforeRouteLeave (to, from, next) {
      document.body.style.overflow = ''
      if (this._telemetryTimer) clearTimeout(this._telemetryTimer)
      this.clearHotspots()
      storage.set(buildProblemCodeKey(this.problem._id), {
        code: this.code,
        language: this.language
      })
      storage.set(buildProblemErrorKey(this.problemID), this.workflowContext.consecutiveErrors)
      next()
    },
    watch: {
      '$route' () {
        this.agentPanelVisible = false
        this.resetWorkflowContext()
        this.init()
      },
      'code' (newVal) {
        if (!this._codeDebounceTimer) {
          this._codeDebounceTimer = setTimeout(() => {
            this._codeDebounceTimer = null
            this.editCount++
            this.lastEditTime = Date.now()
          }, 300)
        }
      },
      'agentPanelVisible' (val) {
        if (val) {
          this._aiTutorUsedSinceLastAC = true
          this.learningTwinVisible = true
        }
        setTimeout(() => {
          var editorRef = this.getEditorRef()
          if (editorRef && typeof editorRef.refreshEditorLayout === 'function') {
            editorRef.refreshEditorLayout()
          }
        }, 400)
      },
      'workflowContext.consecutiveErrors' (nextValue, previousValue) {
        if (nextValue >= 2 && nextValue !== previousValue) {
          this.loadStuckSupplementPlan('error')
        }
        if (nextValue === 0) {
          this.supplementCards = []
          this.supplementIntroMessage = ''
          this.showSupplementCards = false
        }
      }
    }
  }
</script>

<style lang="less" scoped src="./Problem.styles.less"></style>
