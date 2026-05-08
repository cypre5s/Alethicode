<template>
  <transition name="slide-panel">
    <div v-show="visible" class="unified-panel">
      <div class="panel-header">
        <div class="header-left">
          <el-icon :size="18"><School /></el-icon>
          <span class="panel-title">AI 学习助手</span>
        </div>
        <div class="header-actions">
          <div class="profile-btn" @click="profileDrawerVisible = true" title="我的学习画像">
            <el-icon :size="16"><User /></el-icon>
          </div>
          <div class="clear-btn" @click="handleClearChat" title="清空对话">
            <el-icon :size="16"><Delete /></el-icon>
          </div>
          <div class="close-btn" @click="$emit('close')">
            <el-icon :size="22"><Close /></el-icon>
          </div>
        </div>
      </div>

      <div v-if="runtimeStatusVisible" class="runtime-status-area">
        <div v-if="isApprovalState" class="runtime-banner runtime-banner-approval">
          <div class="runtime-banner-icon">
            <el-icon :size="18"><QuestionFilled /></el-icon>
          </div>
          <div class="runtime-banner-body">
            <div class="runtime-banner-title">等待审批确认</div>
            <div v-if="pendingHumanAction" class="runtime-banner-desc">{{ approvalDescription }}</div>
            <div class="runtime-banner-actions">
              <button class="runtime-action-btn runtime-action-confirm" type="button" @click="$emit('approve-action')">
                <el-icon :size="14"><CircleCheck /></el-icon>
                确认
              </button>
              <button class="runtime-action-btn runtime-action-reject" type="button" @click="$emit('reject-action')">
                <el-icon :size="14"><CircleClose /></el-icon>
                拒绝
              </button>
            </div>
          </div>
        </div>

        <div v-else-if="isRestoringState" class="runtime-banner runtime-banner-restoring">
          <div class="runtime-banner-icon">
            <el-icon :size="18" class="runtime-spin"><Loading /></el-icon>
          </div>
          <div class="runtime-banner-body">
            <div class="runtime-banner-title">正在从 checkpoint 恢复</div>
            <div v-if="runtimeContext.checkpointId" class="runtime-banner-desc">
              Checkpoint: {{ runtimeContext.checkpointId }}
            </div>
          </div>
        </div>

        <div v-else-if="isFailedState" class="runtime-banner runtime-banner-failed">
          <div class="runtime-banner-icon">
            <el-icon :size="18"><WarningFilled /></el-icon>
          </div>
          <div class="runtime-banner-body">
            <div class="runtime-banner-title">任务执行失败</div>
            <div v-if="runtimeContext.failureBucket" class="runtime-banner-desc">
              失败类型: {{ runtimeContext.failureBucket }}
            </div>
            <div v-if="runtimeContext.lastError" class="runtime-banner-desc">
              {{ runtimeContext.lastError }}
            </div>
            <div class="runtime-banner-actions">
              <button class="runtime-action-btn runtime-action-restore" type="button" @click="$emit('recover-checkpoint')">
                <el-icon :size="14"><RefreshLeft /></el-icon>
                恢复最近 checkpoint
              </button>
              <button class="runtime-action-btn runtime-action-restart" type="button" @click="$emit('restart-workflow')">
                <el-icon :size="14"><Delete /></el-icon>
                清空重开
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="planRecommendation" class="plan-recommendation-banner">
        <div class="plan-recommendation-top">
          <div class="plan-recommendation-title">建议进入陪练</div>
          <div class="plan-recommendation-reason">{{ planRecommendation.reason }}</div>
        </div>
        <div v-if="planRecommendation.coordinationReasoning" class="plan-recommendation-body">
          {{ planRecommendation.coordinationReasoning }}
        </div>
        <div class="plan-recommendation-actions">
          <button type="button" class="runtime-action-btn runtime-action-confirm" @click="$emit('accept-plan-recommendation')">
            开始陪练
          </button>
          <button type="button" class="runtime-action-btn runtime-action-reject" @click="$emit('dismiss-plan-recommendation')">
            暂时不要
          </button>
        </div>
      </div>

      <div v-if="planSteps && planSteps.length" class="plan-area">
        <PlanStepsCard
          :steps="planSteps"
          :paused="planPaused"
          :completed="planCompleted"
          @confirm-step="handlePlanConfirmStep"
          @skip-step="$emit('plan-skip-step', $event)"
        />
        <div v-if="currentPlanStep" class="current-step-card">
          <div class="current-step-meta">
            <span class="current-step-label">当前一步</span>
            <span class="current-step-role">{{ currentPlanStep.mentor_role }}</span>
          </div>
          <div class="current-step-title">{{ currentPlanStep.title }}</div>
          <div class="current-step-block">
            <span class="current-step-key">为什么做</span>
            <span>{{ currentPlanStep.learning_goal }}</span>
          </div>
          <div class="current-step-block">
            <span class="current-step-key">现在要做什么</span>
            <span>{{ currentPlanStep.student_task }}</span>
          </div>
          <div class="current-step-block">
            <span class="current-step-key">什么算完成</span>
            <span>{{ currentPlanStep.pass_rule }}</span>
          </div>
          <div class="current-step-block">
            <span class="current-step-key">如果不会，从这里开始</span>
            <span>{{ currentPlanStep.support_hint }}</span>
          </div>
        </div>
        <SteeringBar
          :disabled="!sessionId || planSurrendered"
          :plan-paused="planPaused"
          :plan-completed="planCompleted"
          :plan-surrendered="planSurrendered"
          @pause="$emit('plan-pause')"
          @resume="$emit('plan-resume')"
          @take-over="$emit('plan-take-over')"
          @redirect="$emit('plan-redirect', $event)"
        />
        <div v-if="planReasoning" class="plan-reasoning">
          <el-icon :size="12"><ChatDotRound /></el-icon>
          <span>{{ planReasoning }}</span>
        </div>
      </div>

      <div class="message-stream" ref="messageStream">
        <div v-if="timelineItems.length === 0 && !loading" key="welcome-state" class="welcome-state">
          <div class="welcome-icon-wrap">
            <el-icon :size="28"><School /></el-icon>
          </div>
          <h3 class="welcome-title">AI 学习助手</h3>
          <p class="welcome-desc">{{ welcomeData.greeting || '我可以帮你理解题目、整理思路、诊断错误。选择下方操作开始吧：' }}</p>
          <div v-if="welcomeData.memory_tags && welcomeData.memory_tags.length" class="welcome-memory-tags">
            <span v-for="(tag, i) in welcomeData.memory_tags" :key="i" class="welcome-tag">{{ tag }}</span>
          </div>
          <div class="welcome-actions">
            <a
              v-for="action in effectiveWelcomeActions"
              :key="'w-' + action.key"
              class="welcome-action-chip"
              @click="handleQuickAction(action)"
            >
              <el-icon :size="16"><component :is="iconComponents[action.icon] || Lightning" /></el-icon>
              <span>{{ action.label }}</span>
            </a>
          </div>
        </div>

        <div
          v-if="timelineItems.length > 0 || loading"
          class="message-stream-spacer"
          aria-hidden="true"
        ></div>

        <transition-group name="agent-msg-fade" tag="div">
        <div
          v-for="item in timelineItems"
          :key="item._kind === 'checkpoint' ? 'cp-' + item.id : item.type + '-' + item.id"
          class="timeline-item-shell"
        >
          <div v-if="item._kind === 'checkpoint'" class="checkpoint-marker" @click="$emit('restore-checkpoint', item.checkpoint_id)">
            <div class="checkpoint-line"></div>
            <span class="checkpoint-label">
              <el-icon :size="12"><Flag /></el-icon>
              {{ item.label || 'Checkpoint' }}
            </span>
            <div class="checkpoint-line"></div>
          </div>

          <div v-else-if="item.type === 'system'" class="system-msg">
            <span class="system-dot"></span>
            {{ item.content }}
          </div>

          <div v-else-if="item.type === 'user'" class="user-msg">
            <div class="user-bubble">{{ item.content }}</div>
          </div>

          <div v-else-if="item.type === 'ai_reply'" class="ai-msg">
            <div v-if="cardCharacter(item.type)" class="ai-avatar ai-avatar-char" :style="{ borderColor: cardCharacter(item.type).color }">
              <img :src="cardCharacterSprite(item.type)" class="ai-avatar-sprite" style="width:28px;height:28px;max-width:28px;max-height:28px" />
            </div>
            <div v-else class="ai-avatar"><el-icon :size="14"><School /></el-icon></div>
            <div class="ai-bubble" v-html="renderMarkdown(item.content)"></div>
            <div class="msg-actions" @click="$emit('regenerate', { messageId: item.id })">
              <el-icon :size="14"><Refresh /></el-icon>
            </div>
          </div>

          <div v-else-if="item.type === 'problem_guide'" class="char-card-wrap" :style="cardCharStyle(item.type)">
            <div v-if="cardCharacter(item.type)" class="char-card-label">
              <img :src="cardCharacterSprite(item.type)" class="char-card-avatar" style="width:22px;height:22px;max-width:22px;max-height:22px" />
              <span class="char-card-name" :style="{ color: cardCharacter(item.type).color }">{{ cardCharacter(item.type).name }}</span>
            </div>
            <ProblemGuideCard
              :data="item.data"
              :can-start-ideate="canStartIdeate"
              @ask-question="handleQuickQuestion"
              @start-ideate="handleStartIdeate"
              @open-courseware-ref="handleOpenCoursewareRef($event, item.data.courseware_refs || [])"
            />
            <div class="agent-feedback-row">
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'helpful' }]"
                @click="submitFeedback(item, 'helpful')"
                aria-label="反馈有帮助"
              >👍 有帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'unhelpful' }]"
                @click="submitFeedback(item, 'unhelpful')"
                aria-label="反馈没帮助"
              >👎 没帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'confusing' }]"
                @click="submitFeedback(item, 'confusing')"
                aria-label="反馈看不懂"
              >❓ 看不懂</button>
            </div>
          </div>

          <div v-else-if="item.type === 'ideate_analysis'" class="char-card-wrap" :style="cardCharStyle(item.type)">
            <div v-if="cardCharacter(item.type)" class="char-card-label">
              <img :src="cardCharacterSprite(item.type)" class="char-card-avatar" style="width:22px;height:22px;max-width:22px;max-height:22px" />
              <span class="char-card-name" :style="{ color: cardCharacter(item.type).color }">{{ cardCharacter(item.type).name }}</span>
            </div>
          <IdeateAnalysisCard
            :data="item.data"
            :can-request-skeleton="canRequestSkeleton"
            @request-skeleton="handleRequestSkeleton"
          />
          </div>

          <SkeletonCodeCard
            v-else-if="item.type === 'skeleton_code'"
            :data="item.data"
            @insert-code="$emit('insert-code', $event)"
            @request-parsons="$emit('request-parsons')"
          />

          <div v-else-if="item.type === 'error_diagnosis'">
            <ErrorDiagnosisCard
              :data="item.data"
              :execution-trace="executionTrace"
              :student-code="studentCode"
              :can-request-execution-trace="canRequestExecutionTrace"
              @ask-question="handleQuickQuestion"
              @open-courseware-ref="handleOpenCoursewareRef($event, item.data.courseware_refs || [])"
              @highlight-errors="$emit('highlight-errors', $event)"
              @insert-code="$emit('insert-code', $event)"
              @request-execution-trace="$emit('request-execution-trace', { source: 'error_diagnosis', errorDiagnosis: item.data })"
            />
            <div class="agent-feedback-row">
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'helpful' }]"
                @click="submitFeedback(item, 'helpful')"
                aria-label="反馈有帮助"
              >👍 有帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'unhelpful' }]"
                @click="submitFeedback(item, 'unhelpful')"
                aria-label="反馈没帮助"
              >👎 没帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'confusing' }]"
                @click="submitFeedback(item, 'confusing')"
                aria-label="反馈看不懂"
              >❓ 看不懂</button>
            </div>
          </div>

          <ExecutionTraceExplainerCard
            v-else-if="item.type === 'execution_trace_explainer'"
            :data="item.data"
          />

          <div v-else-if="item.type === 'post_ac'">
            <PostACCard
              :data="item.data"
            />
            <div class="agent-feedback-row">
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'helpful' }]"
                @click="submitFeedback(item, 'helpful')"
                aria-label="反馈有帮助"
              >👍 有帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'unhelpful' }]"
                @click="submitFeedback(item, 'unhelpful')"
                aria-label="反馈没帮助"
              >👎 没帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'confusing' }]"
                @click="submitFeedback(item, 'confusing')"
                aria-label="反馈看不懂"
              >❓ 看不懂</button>
            </div>
          </div>

          <div v-else-if="item.type === 'transfer_problem'">
            <TransferProblemCard
              :data="item.data"
            />
            <div class="agent-feedback-row">
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'helpful' }]"
                @click="submitFeedback(item, 'helpful')"
                aria-label="反馈有帮助"
              >👍 有帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'unhelpful' }]"
                @click="submitFeedback(item, 'unhelpful')"
                aria-label="反馈没帮助"
              >👎 没帮助</button>
              <button
                type="button"
                :class="['fb-btn', { 'fb-active': feedbackMap[item.id] === 'confusing' }]"
                @click="submitFeedback(item, 'confusing')"
                aria-label="反馈看不懂"
              >❓ 看不懂</button>
            </div>
          </div>

          <KnowledgeReviewCard
            v-else-if="item.type === 'knowledge_review'"
            :data="item.data"
            :feedback-value="feedbackMap[item.id] || ''"
            @feedback="submitFeedback(item, $event)"
            @open-courseware-ref="openCoursewareInNewTab"
          />

          <VisualizeRenderer
            v-else-if="item.type === 'visualize'"
            :data="item.data"
          />

          <EncouragementCard
            v-else-if="item.type === 'encouragement'"
            :data="item.data"
            :character="encourageChar"
            :character-sprite="encourageCharSprite"
            :character-line="encourageCharLine"
            @open-courseware-ref="openCoursewareInNewTab"
            @open-recovery-problem="(rp) => $router.push('/problem/' + (rp.problem_display_id || rp.problem_key))"
          />

          <ParsonsProblemCard
            v-else-if="item.type === 'parsons'"
            :data="item.data"
            :submitting="!!parsonsState && parsonsState.submitting"
            :hint="(parsonsState && parsonsState.hint) || ''"
            :last-result="(parsonsState && parsonsState.lastResult) || null"
            @submit="(order) => $emit('parsons-submit', { sessionId: item.data && item.data.parsons_session_id, order })"
            @reset="$emit('parsons-reset', { sessionId: item.data && item.data.parsons_session_id })"
          />

          <div v-else-if="item.type === 'error'" class="error-msg">
            <el-icon :size="14" color="#EF4444"><WarningFilled /></el-icon>
            <span>{{ item.content }}</span>
          </div>
        </div>
        </transition-group>

        <div v-if="loading" class="loading-area">
          <div class="loading-phase-text">
            <el-icon class="is-loading" :size="14"><Loading /></el-icon>
            <span>{{ loadingPhaseText }}</span>
          </div>
          <div class="loading-skeleton">
            <div class="wave-line" style="width: 80%"></div>
            <div class="wave-line" style="width: 60%"></div>
            <div class="wave-line" style="width: 70%"></div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div v-if="inputMode === 'ideate'" class="ideate-input-hint">
          <el-icon :size="14" color="#D97706"><Sunny /></el-icon>
          用自然语言描述你的思路（至少10个字）
        </div>

        <ContextUsageBar
          v-if="contextUsage && contextUsage.tokens_limit"
          :tokens-used="contextUsage.tokens_used || 0"
          :tokens-limit="contextUsage.tokens_limit || 0"
          :model-name="contextUsage.model_name || ''"
          @compact-click="handleCompactPlaceholder"
        />

        <AtMentionMenu
          :visible="atMenuVisible && inputMode === 'chat'"
          :groups="atGroups"
          :active-index="atActiveIndex"
          @select="composerHandlers.selectAtItem"
          @close="composerHandlers.refreshProvider('coursewares')"
        />

        <SlashCommandMenu
          :visible="slashMenuVisible"
          :groups="slashGroups"
          :active-index="slashActiveIndex"
          @select="composerHandlers.selectSlashItem"
        />

        <div class="input-row">
          <el-input
            type="textarea"
            :rows="2"
            :model-value="rawText"
            @update:model-value="composerHandlers.onInput"
            :placeholder="inputPlaceholder"
            :disabled="!canChatInput || loading || isInputBlocked"
            @keydown="composerHandlers.onKeydown"
            class="panel-input"
          />
          <button
            :class="['action-btn', { 'is-stop': loading }]"
            :disabled="loading ? false : (!canChatInput || !rawText.trim() || isInputBlocked)"
            @click="loading ? $emit('stop-agent') : handleSend()"
            :title="loading ? '中断生成' : '发送'"
          >
            <el-icon v-if="loading" :size="14"><VideoPause /></el-icon>
            <el-icon v-else :size="16"><ArrowUp /></el-icon>
          </button>
        </div>

        <ComposerHintBar
          :at-active="atMenuVisible"
          :slash-active="slashMenuVisible"
        />

        <div class="quick-actions">
          <a
            v-for="action in filteredQuickActions"
            :key="action.key"
            :class="{ 'is-disabled': isInputBlocked }"
            @click="isInputBlocked ? null : handleQuickAction(action)"
          >
            <el-icon :size="12"><component :is="iconComponents[action.icon] || Lightning" /></el-icon>
            {{ action.label }}
          </a>
        </div>
      </div>
    </div>
  </transition>

  <el-dialog
    v-model="coursewarePreviewVisible"
    append-to-body
    destroy-on-close
    class="courseware-preview-dialog"
    width="92%"
    @closed="handleCoursewarePreviewClosed"
  >
    <template #header>
      <div class="courseware-dialog-header">
        <div class="courseware-dialog-title-wrap">
          <el-icon :size="18"><Reading /></el-icon>
          <div class="courseware-dialog-title-group">
            <div class="courseware-dialog-title">课件预览</div>
            <div class="courseware-dialog-meta">
              {{ activeCoursewareMeta }}
            </div>
          </div>
        </div>
        <a
          v-if="coursewarePreviewFrameUrl"
          class="courseware-dialog-open-link"
          :href="coursewarePreviewFrameUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          新标签打开完整课件
        </a>
      </div>
    </template>

    <div class="courseware-dialog-layout">
      <aside class="courseware-dialog-sidebar">
        <div class="courseware-dialog-sidebar-title">引用列表</div>
        <button
          v-for="(ref, idx) in coursewarePreviewRefs"
          :key="coursewareRefKey(ref, idx)"
          type="button"
          class="courseware-dialog-ref-item"
          :class="{ 'is-active': isSelectedCoursewareRef(ref) }"
          @click="selectCoursewareRef(ref)"
        >
          <div class="courseware-dialog-ref-top">
            <span class="courseware-dialog-ref-doc">{{ ref.document_title || '课件文档' }}</span>
            <span class="courseware-dialog-ref-page">P{{ ref.slide_number || ref.page_no }}</span>
          </div>
          <div class="courseware-dialog-ref-preview">{{ ref.preview || '暂无命中片段' }}</div>
        </button>
      </aside>

      <section class="courseware-dialog-main">
        <div v-if="coursewarePreviewLoading" class="courseware-dialog-loading">
          <el-skeleton animated :rows="8" />
        </div>

        <div v-else-if="coursewarePreviewError" class="courseware-dialog-error">
          {{ coursewarePreviewError }}
        </div>

        <div v-else-if="coursewarePreviewPage" class="courseware-dialog-content">
          <div class="courseware-dialog-frame-wrap">
            <iframe
              :src="coursewarePreviewFrameUrl"
              class="courseware-dialog-iframe"
              title="课件预览"
            ></iframe>
          </div>

          <div class="courseware-dialog-text-grid">
            <div class="courseware-dialog-text-card">
              <div class="courseware-dialog-text-title">命中片段</div>
              <div class="courseware-dialog-text-body">{{ selectedCoursewareRef && selectedCoursewareRef.preview ? selectedCoursewareRef.preview : '暂无命中片段。' }}</div>
            </div>
            <div class="courseware-dialog-text-card">
              <div class="courseware-dialog-text-title">当前页正文</div>
              <div class="courseware-dialog-text-body">{{ coursewarePreviewPage.page_text || coursewarePreviewPage.excerpt || '当前页暂无可显示正文。' }}</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </el-dialog>

  <ProfileDrawer v-model="profileDrawerVisible" />

  <ParsonsWalkthroughDialog
    :visible="!!parsonsWalkthrough && parsonsWalkthrough.visible"
    :loading="!!parsonsWalkthrough && parsonsWalkthrough.loading"
    :score="(parsonsWalkthrough && parsonsWalkthrough.score) || 0"
    :feedback="(parsonsWalkthrough && parsonsWalkthrough.feedback) || ''"
    :last-passed="!!parsonsWalkthrough && parsonsWalkthrough.lastPassed"
    :can-rewrite="!!parsonsWalkthrough && parsonsWalkthrough.canRewrite"
    :attempts="(parsonsWalkthrough && parsonsWalkthrough.attempts) || 0"
    @submit="(text) => $emit('parsons-walkthrough-submit', { text })"
    @continue="$emit('parsons-walkthrough-continue')"
  />
  <MotionOverlay ref="moRef" />
</template>

<script>
import { markRaw, ref, computed, defineAsyncComponent, watch } from 'vue'
import api from '@oj/api'
import { checkInputSequence } from '@oj/utils/inputValidator'
const MotionOverlay = defineAsyncComponent(() => import('@oj/components/MotionOverlay.vue'))
import { fetchCoursewarePreviewPage } from './workflowServerState'
import { getCharacterForCardType, getCharacter, getSpritePath, getExpressionForEvent } from './characterConfig'
import { useChatComposer } from '@oj/components/chat/useChatComposer'
import AtMentionMenu from '@oj/components/chat/AtMentionMenu.vue'
import SlashCommandMenu from '@oj/components/chat/SlashCommandMenu.vue'
import ComposerHintBar from '@oj/components/chat/ComposerHintBar.vue'
import ContextUsageBar from '@oj/components/chat/ContextUsageBar.vue'
import ProblemGuideCard from './cards/ProblemGuideCard.vue'
import IdeateAnalysisCard from './cards/IdeateAnalysisCard.vue'
import SkeletonCodeCard from './cards/SkeletonCodeCard.vue'
import ErrorDiagnosisCard from './cards/ErrorDiagnosisCard.vue'
import ExecutionTraceExplainerCard from './cards/ExecutionTraceExplainerCard.vue'
import PostACCard from './cards/PostACCard.vue'
import TransferProblemCard from './cards/TransferProblemCard.vue'
import KnowledgeReviewCard from './cards/KnowledgeReviewCard.vue'
import EncouragementCard from './cards/EncouragementCard.vue'
import VisualizeRenderer from './cards/visualize/VisualizeRenderer.vue'
import ParsonsProblemCard from './cards/ParsonsProblemCard.vue'
import ParsonsWalkthroughDialog from './cards/parsons/ParsonsWalkthroughDialog.vue'
import PlanStepsCard from './PlanStepsCard.vue'
import SteeringBar from './SteeringBar.vue'
import ProfileDrawer from './profile/ProfileDrawer.vue'
import marked from 'marked'
import { sanitize } from '@/utils/sanitize'
import { notify } from '@/utils/notifications'
import { ElMessageBox } from 'element-plus'
import {
  Reading, Sunny, Monitor, Warning, StarFilled, Sort,
  CircleCheck, CircleClose, DArrowRight, Lightning,
  School, Delete, Close, Flag, Refresh, RefreshLeft,
  WarningFilled, VideoPause, ArrowUp, QuestionFilled, Loading,
  ChatDotRound, User, Grid, Collection, Document
} from '@element-plus/icons-vue'

const ICON_COMPONENTS = markRaw({
  Reading,
  Sunny,
  Monitor,
  Warning,
  StarFilled,
  Sort,
  CircleCheck,
  DArrowRight,
  Lightning,
  Grid,
  Collection,
  Document
})

export default {
  name: 'UnifiedAgentPanel',
  emits: [
    'close',
    'send',
    'trigger-agent',
    'switch-input-mode',
    'show-warmup',
    'request-skeleton',
    'request-parsons',
    'request-transfer',
    'highlight-errors',
    'insert-code',
    'clear-highlights',
    'navigate-problem',
    'stop-agent',
    'restore-checkpoint',
    'regenerate',
    'clear-chat',
    'report-event',
    'request-execution-trace',
    'approve-action',
    'reject-action',
    'recover-checkpoint',
    'restart-workflow',
    'accept-plan-recommendation',
    'dismiss-plan-recommendation',
    'plan-confirm-step',
    'plan-skip-step',
    'plan-pause',
    'plan-resume',
    'plan-take-over',
    'plan-redirect',
    'request-visualize',
    'parsons-submit',
    'parsons-reset',
    'parsons-walkthrough-submit',
    'parsons-walkthrough-continue',
    'compact-session',
    'fork-session'
  ],
  components: {
    ProblemGuideCard,
    IdeateAnalysisCard,
    SkeletonCodeCard,
    ErrorDiagnosisCard,
    ExecutionTraceExplainerCard,
    PostACCard,
    TransferProblemCard,
    KnowledgeReviewCard,
    EncouragementCard,
    VisualizeRenderer,
    ParsonsProblemCard,
    ParsonsWalkthroughDialog,
    PlanStepsCard,
    SteeringBar,
    ProfileDrawer,
    AtMentionMenu,
    SlashCommandMenu,
    ComposerHintBar,
    ContextUsageBar,
    MotionOverlay,
    Reading, Sunny, Monitor, Warning, StarFilled, Sort,
    CircleCheck, CircleClose, DArrowRight, Lightning,
    School, Delete, Close, Flag, Refresh, RefreshLeft,
    WarningFilled, VideoPause, ArrowUp, QuestionFilled, Loading,
    ChatDotRound, User
  },
  props: {
    visible: { type: Boolean, default: false },
    messages: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    inputMode: { type: String, default: 'chat' },
    quickActions: { type: Array, default: () => [] },
    checkpoints: { type: Array, default: () => [] },
    executionTrace: { type: Array, default: () => [] },
    studentCode: { type: String, default: '' },
    sessionId: { type: String, default: '' },
    problemId: { type: [Number, String], default: 0 },
    languagePackId: { type: [Number, String], default: null },
    workflowQueryClient: { type: Object, default: null },
    canChatInput: { type: Boolean, default: true },
    canStartIdeate: { type: Boolean, default: true },
    canRequestSkeleton: { type: Boolean, default: true },
    canRequestExecutionTrace: { type: Boolean, default: true },
    runtimeContext: { type: Object, default: () => ({}) },
    pendingHumanAction: { type: String, default: '' },
    studentEvent: { type: String, default: '' },
    planSteps: { type: Array, default: () => [] },
    planPaused: { type: Boolean, default: false },
    planCompleted: { type: Boolean, default: false },
    planSurrendered: { type: Boolean, default: false },
    planRecommendation: { type: Object, default: null },
    planReasoning: { type: String, default: '' },
    lastConversationCards: { type: Array, default: () => [] },
    parsonsState: { type: Object, default: () => ({ submitting: false, hint: '', lastResult: null }) },
    parsonsWalkthrough: { type: Object, default: () => ({ visible: false, loading: false, score: 0, feedback: '', lastPassed: false, canRewrite: false }) },
    contextUsage: { type: Object, default: () => ({ tokens_used: 0, tokens_limit: 0, model_name: '' }) }
  },
  setup (props, { emit }) {
    const moRef = ref(null)
    const coursewarePack = ref(null)
    const coursewarePackLoaded = ref(false)
    const coursewarePackLoading = ref(false)
    const coursewareDocuments = ref([])
    const coursewareDocumentsLoaded = ref(false)
    const coursewareDocumentsLoading = ref(false)
    const knowledgeComponentItems = ref([])
    const knowledgeComponentsLoaded = ref(false)
    const knowledgeComponentsLoading = ref(false)
    const learnerNotebookItems = ref([])
    const learnerNotebooksLoaded = ref(false)
    const learnerNotebooksLoading = ref(false)
    let knowledgeComponentsLoadPromise = null
    let learnerNotebooksLoadPromise = null

    /**
     * 通过 listQaPacks 反查当前 problem.language_pack_id 对应的课件包基础信息。
     * 命中失败（无权限 / 未关联课件 / 服务故障）一律返回空对象，不阻塞 @ 菜单其他组。
     */
    function ensureCurrentCoursewarePackLoaded () {
      const lpId = props.languagePackId
      if (!lpId) {
        coursewarePackLoaded.value = true
        coursewarePack.value = null
        return Promise.resolve()
      }
      if (coursewarePackLoaded.value || coursewarePackLoading.value) return Promise.resolve()
      coursewarePackLoading.value = true
      return api.getLanguagePackQaPacks().then(res => {
        const packs = res && res.data && Array.isArray(res.data.data) ? res.data.data : []
        coursewarePack.value = packs.find(p => p && String(p.id) === String(lpId)) || null
        coursewarePackLoaded.value = true
      }).catch(err => {
        console.warn('[UnifiedAgentPanel] load courseware pack failed:', err && err.message)
        coursewarePack.value = null
      }).finally(() => {
        coursewarePackLoading.value = false
      })
    }

    /**
     * 拉取当前 problem.language_pack_id 下所有 normalized 文档（一份 PDF = 一章）。
     * 章号取后端按 (sort_order, id) 排序后的 1-based 序号；按章号产出 @page:章.页 candidate。
     */
    function ensureCoursewareDocumentsLoaded () {
      const lpId = props.languagePackId
      if (!lpId) {
        coursewareDocumentsLoaded.value = true
        coursewareDocuments.value = []
        return Promise.resolve()
      }
      if (coursewareDocumentsLoaded.value || coursewareDocumentsLoading.value) return Promise.resolve()
      coursewareDocumentsLoading.value = true
      return api.getLanguagePackDocuments(lpId).then(res => {
        const docs = res && res.data && Array.isArray(res.data.data) ? res.data.data : []
        coursewareDocuments.value = docs.filter(doc => doc && doc.status === 'normalized')
        coursewareDocumentsLoaded.value = true
      }).catch(err => {
        console.warn('[UnifiedAgentPanel] load courseware documents failed:', err && err.message)
        coursewareDocuments.value = []
      }).finally(() => {
        coursewareDocumentsLoading.value = false
      })
    }

    function ensureKnowledgeComponentsLoaded () {
      if (!props.languagePackId) {
        knowledgeComponentsLoaded.value = true
        knowledgeComponentItems.value = []
        return Promise.resolve()
      }
      if (knowledgeComponentsLoaded.value) return Promise.resolve()
      if (knowledgeComponentsLoadPromise) return knowledgeComponentsLoadPromise
      knowledgeComponentsLoading.value = true
      const requestedLanguagePackId = props.languagePackId
      const loadPromise = api.getKcGraph(requestedLanguagePackId).then(res => {
        if (String(props.languagePackId || '') !== String(requestedLanguagePackId || '')) return
        const payload = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : {})
        const nodes = Array.isArray(payload.nodes) ? payload.nodes : []
        knowledgeComponentItems.value = nodes.map(node => {
          const id = node && node.id != null ? String(node.id) : ''
          return {
            key: 'kc:' + id,
            token: '@kc:' + id,
            label: node && node.name ? node.name : ('知识点 ' + id),
            desc: node && node.chapter_title ? node.chapter_title : '',
            hoverPreview: node && node.description ? node.description : (node && node.chapter_title ? node.chapter_title : '')
          }
        }).filter(item => item.token !== '@kc:')
        knowledgeComponentsLoaded.value = true
      }).catch(err => {
        if (String(props.languagePackId || '') !== String(requestedLanguagePackId || '')) return
        console.warn('[UnifiedAgentPanel] load knowledge components failed:', err && err.message)
        knowledgeComponentItems.value = []
      }).finally(() => {
        if (knowledgeComponentsLoadPromise === loadPromise) {
          knowledgeComponentsLoading.value = false
          knowledgeComponentsLoadPromise = null
        }
      })
      knowledgeComponentsLoadPromise = loadPromise
      return knowledgeComponentsLoadPromise
    }

    function buildKnowledgeComponentItems () {
      return Array.isArray(knowledgeComponentItems.value) ? knowledgeComponentItems.value : []
    }

    function ensureLearnerNotebooksLoaded () {
      if (learnerNotebooksLoaded.value) return Promise.resolve()
      if (learnerNotebooksLoadPromise) return learnerNotebooksLoadPromise
      learnerNotebooksLoading.value = true
      const loadPromise = api.getLearnerNotebook({}).then(res => {
        const payload = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : {})
        const entries = Array.isArray(payload.entries) ? payload.entries : []
        learnerNotebookItems.value = entries.map(entry => {
          const id = entry && entry.id != null ? String(entry.id) : ''
          const label = entry && (entry.title || entry.problem_title || entry.error_taxonomy)
            ? (entry.title || entry.problem_title || entry.error_taxonomy)
            : ('笔记 ' + id)
          const desc = entry && (entry.reflection || entry.root_cause || entry.breakthrough_insight || entry.content)
            ? (entry.reflection || entry.root_cause || entry.breakthrough_insight || entry.content)
            : ''
          return {
            key: 'notebook:' + id,
            token: '@notebook:' + id,
            label,
            desc: String(desc).slice(0, 80),
            hoverPreview: String(desc).slice(0, 180)
          }
        }).filter(item => item.token !== '@notebook:')
        learnerNotebooksLoaded.value = true
      }).catch(err => {
        console.warn('[UnifiedAgentPanel] load learner notebooks failed:', err && err.message)
        learnerNotebookItems.value = []
      }).finally(() => {
        if (learnerNotebooksLoadPromise === loadPromise) {
          learnerNotebooksLoading.value = false
          learnerNotebooksLoadPromise = null
        }
      })
      learnerNotebooksLoadPromise = loadPromise
      return learnerNotebooksLoadPromise
    }

    function buildLearnerNotebookItems () {
      return Array.isArray(learnerNotebookItems.value) ? learnerNotebookItems.value : []
    }

    function formatReferenceDescription (raw, cardType) {
      const TYPE_HINTS = {
        problem_guide: '引用最近的题目导读卡片',
        ideate_analysis: '引用最近的思路分析卡片',
        skeleton_code: '引用最近的骨架代码卡片',
        error_diagnosis: '引用最近的错误诊断卡片',
        post_ac: '引用最近的过题总结卡片',
        transfer_problem: '引用最近的迁移题卡片',
        knowledge_review: '引用最近的知识点回顾卡片',
        visualize: '引用最近的教学可视化卡片',
        parsons_problem: '引用最近的拼装挑战卡片'
      }
      let text = ''
      if (typeof raw === 'string') {
        text = raw.trim()
      } else if (raw && typeof raw === 'object') {
        text = raw.review_content || raw.root_cause || raw.analysis || raw.plain_task || raw.alt_text || raw.title || ''
      }
      if (text.startsWith('{') || text.startsWith('[')) {
        try {
          const parsed = JSON.parse(text)
          text = parsed.review_content || parsed.root_cause || parsed.analysis || parsed.plain_task || parsed.alt_text || parsed.title || ''
        } catch {
          text = ''
        }
      }
      text = String(text || TYPE_HINTS[cardType] || '引用这张卡片').replace(/\s+/g, ' ').trim()
      return text.length > 42 ? text.slice(0, 42) + '…' : text
    }

    const TYPE_LABELS = {
      problem_guide: '题目导读',
      ideate_analysis: '思路分析',
      skeleton_code: '骨架代码',
      error_diagnosis: '错误诊断',
      post_ac: '过题总结',
      transfer_problem: '迁移题',
      knowledge_review: '知识点回顾',
      visualize: '教学可视化',
      parsons_problem: '拼装挑战'
    }
    const SHORTHAND_BY_TYPE = {
      problem_guide: 'guide',
      ideate_analysis: 'ideate',
      error_diagnosis: 'error',
      post_ac: 'post_ac',
      transfer_problem: 'transfer',
      knowledge_review: 'review',
      visualize: 'visualize'
    }

    function buildCardItems () {
      const cards = []
      const seenTypes = new Set()
      const conversationCards = Array.isArray(props.lastConversationCards) ? props.lastConversationCards : []
      conversationCards
        .filter(card => card && (card.card_id || card.card_type))
        .forEach(card => {
          const token = card.card_id ? '@card:' + card.card_id : '@last_' + (SHORTHAND_BY_TYPE[card.card_type] || 'review')
          cards.push({
            key: card.card_id || ('last-' + card.card_type),
            token: token,
            label: TYPE_LABELS[card.card_type] || card.card_type || '卡片',
            desc: formatReferenceDescription(card.short_text || card.summary, card.card_type),
            hoverPreview: formatReferenceDescription(card.short_text || card.summary, card.card_type)
          })
          if (card.card_type) seenTypes.add(card.card_type)
        })
      const messages = Array.isArray(props.messages) ? props.messages : []
      messages
        .filter(item => item && SHORTHAND_BY_TYPE[item.type] && !seenTypes.has(item.type))
        .forEach(item => {
          cards.push({
            key: 'last-' + item.type,
            token: '@last_' + SHORTHAND_BY_TYPE[item.type],
            label: TYPE_LABELS[item.type] || item.type,
            desc: formatReferenceDescription(item.content || item.title, item.type),
            hoverPreview: formatReferenceDescription(item.content || item.title, item.type)
          })
          seenTypes.add(item.type)
        })
      return cards
    }

    /**
     * 课件整包 fallback：仅当当前 problem 关联了课件包时才暴露 @courseware:<lpId>，
     * 不再展示其他课件包，避免 @ 菜单串到当前题目无关的课件。
     */
    function buildCurrentCoursewareItems () {
      const lpId = props.languagePackId
      if (!lpId) return []
      const pack = coursewarePack.value || { id: lpId }
      const name = pack && pack.name ? pack.name : ('LP-' + lpId)
      const desc = pack && pack.description
        ? pack.description
        : (pack && pack.documents_count != null ? pack.documents_count + ' 份文档' : '整包 RAG 检索')
      return [{
        key: 'courseware-' + lpId,
        token: '@courseware:' + pack.id,
        label: '课件 · ' + name,
        desc,
        hoverPreview: desc
      }]
    }

    /**
     * 二级目录的「课件页」候选项：
     *   章号 = normalized 文档按 (sort_order, id) 的 1-based 序号；
     *   每章下展开 page_count 个候选，subgroup 标签让 AtMentionMenu 渲染独立小节，
     *   token 形如 @page:1.7（章.页），后端 ReferenceResolver 按当前 lp 推断 lpId。
     */
    function buildCoursewarePageItems () {
      const docs = Array.isArray(coursewareDocuments.value) ? coursewareDocuments.value : []
      if (!docs.length) return []
      const items = []
      docs.forEach((doc, idx) => {
        const chapter = idx + 1
        const docTitle = doc && doc.original_filename ? doc.original_filename : '课件 ' + chapter
        const subgroup = '第 ' + chapter + ' 章 · ' + docTitle
        const total = Math.max(0, Number(doc && doc.page_count) || 0)
        for (let p = 1; p <= total; p++) {
          items.push({
            key: 'page-' + chapter + '-' + p,
            token: '@page:' + chapter + '.' + p,
            label: '第 ' + p + ' 页',
            desc: docTitle,
            subgroup
          })
        }
      })
      return items
    }

    const atProviders = [
      { key: 'cards', group: '会话卡片', items: buildCardItems },
      {
        key: 'coursewares',
        group: '课件 · 当前课程包',
        lazyLoad: true,
        items: () => ensureCurrentCoursewarePackLoaded().then(() => buildCurrentCoursewareItems())
      },
      {
        key: 'courseware-pages',
        group: '课件页 · 当前课程包',
        lazyLoad: true,
        items: () => ensureCoursewareDocumentsLoaded().then(() => buildCoursewarePageItems())
      },
      {
        key: 'knowledge-components',
        group: '知识点 · 当前课程包',
        maxInitialDisplay: 8,
        lazyLoad: true,
        items: () => ensureKnowledgeComponentsLoaded().then(() => buildKnowledgeComponentItems())
      },
      {
        key: 'learner-notebooks',
        group: '学习笔记',
        maxInitialDisplay: 6,
        lazyLoad: true,
        items: () => ensureLearnerNotebooksLoaded().then(() => buildLearnerNotebookItems())
      }
    ]

    function getCurrentPlanStep () {
      const steps = Array.isArray(props.planSteps) ? props.planSteps : []
      if (!steps.length) return null
      return steps.find(step => ['active', 'current', 'in_progress'].includes(String(step.status || '').toLowerCase())) ||
        steps.find(step => String(step.status || '').toLowerCase() === 'pending') ||
        steps[0]
    }

    function exportConversationMarkdown () {
      const messages = Array.isArray(props.messages) ? props.messages : []
      if (!messages.length) return
      const header = '# 学习对话导出\n\n时间：' + new Date().toLocaleString() + '\n\n'
      const body = messages.map(msg => {
        const role = msg.type === 'user' ? '我' : (msg.type === 'system' ? '系统' : 'AI 助手')
        const text = (msg.content || msg.title || '')
        return '## ' + role + '\n\n' + text + '\n'
      }).join('\n')
      const blob = new Blob([header + body], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'chat-' + (props.sessionId || 'session') + '-' + Date.now() + '.md'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    }

    /**
     * 检查基于 quickActions（后端 available_actions 投影）的动作是否当前可用。
     * /ideate 和 /guide 是引导动作，始终可用；其余 Agent 动作需后端确认。
     */
    function requireActionAvailable (actionKey, label) {
      const ALWAYS_ALLOWED = new Set(['ideate', 'problem_guide'])
      if (ALWAYS_ALLOWED.has(actionKey)) return true
      const actions = Array.isArray(props.quickActions) ? props.quickActions : []
      if (actions.some(a => a.key === actionKey)) return true
      notify.warning('当前状态下「' + label + '」不可用')
      return false
    }

    const slashCommands = [
      { key: 'cmd-ideate', group: 'Agent 动作', command: '/ideate', label: '思路分析', hint: '描述你的思路', run: () => emit('switch-input-mode', 'ideate') },
      { key: 'cmd-guide', group: 'Agent 动作', command: '/guide', label: '题目导读', run: () => emit('trigger-agent', { key: 'problem_guide', event: 'PROBLEM_GUIDE' }) },
      { key: 'cmd-error', group: 'Agent 动作', command: '/error', label: '错误诊断', run: () => { if (requireActionAvailable('error_chain', '错误诊断')) emit('trigger-agent', { key: 'error_chain', event: 'ERROR_DIAGNOSIS' }) } },
      { key: 'cmd-skeleton', group: 'Agent 动作', command: '/skeleton', label: '骨架代码', run: () => { if (requireActionAvailable('skeleton', '骨架代码')) emit('request-skeleton') } },
      { key: 'cmd-transfer', group: 'Agent 动作', command: '/transfer', label: '迁移题', run: () => { if (requireActionAvailable('transfer', '迁移题')) emit('trigger-agent', { key: 'transfer', event: 'TRANSFER' }) } },
      { key: 'cmd-review', group: 'Agent 动作', command: '/review', label: '知识点回顾', run: () => { if (requireActionAvailable('knowledge_review', '知识点回顾')) emit('trigger-agent', { key: 'knowledge_review', event: 'KNOWLEDGE_REVIEW' }) } },
      { key: 'cmd-postac', group: 'Agent 动作', command: '/post-ac', label: '过题总结', run: () => { if (requireActionAvailable('ac_review', '过题总结')) emit('trigger-agent', { key: 'post_ac', event: 'POST_AC' }) } },
      { key: 'cmd-visualize', group: 'Agent 动作', command: '/visualize', label: '教学可视化', run: () => { if (requireActionAvailable('visualize', '教学可视化')) emit('request-visualize') } },
      { key: 'cmd-clear', group: '会话控制', command: '/clear', label: '清空对话', run: () => emit('clear-chat') },
      { key: 'cmd-export', group: '会话控制', command: '/export', label: '导出 Markdown', run: () => exportConversationMarkdown() },
      {
        key: 'cmd-compact', group: '会话进阶', command: '/compact', label: '压缩上下文',
        status: 'available',
        run: () => emit('compact-session')
      },
      {
        key: 'cmd-fork', group: '会话进阶', command: '/fork', label: '分叉会话',
        status: 'available',
        run: () => emit('fork-session')
      },
      {
        key: 'cmd-resume', group: '会话进阶', command: '/resume', label: '恢复会话',
        status: 'placeholder',
        onPlaceholder: () => notify.info('会话恢复将在 Phase 3 上线')
      }
    ]

    const scopeKey = computed(() => 'tutor:' + (props.problemId || props.sessionId || 'default'))

    const isInputBlocked = computed(() => {
      if (!props.canChatInput) return true
      if (props.loading) return true
      const ctx = props.runtimeContext || {}
      return ctx.runtimeState === 'WAITING_HUMAN_APPROVAL' || ctx.runtimeState === 'RESTORING'
    })

    const composer = useChatComposer({
      scopeKey: scopeKey,
      atProviders: atProviders,
      slashCommands: slashCommands,
      isInputBlocked: isInputBlocked,
      onSubmit: async (text) => {
        if (await checkInputSequence(text)) {
          composer.handlers.clear()
          if (moRef.value && moRef.value.play) moRef.value.play()
          return
        }
        const currentStep = getCurrentPlanStep()
        if (currentStep && !props.planPaused && !props.planCompleted && !props.planSurrendered) {
          emit('plan-confirm-step', { step: currentStep, responseText: text })
          return
        }
        emit('send', { text: text, mode: props.inputMode })
      }
    })

    // 切题 / 切课件包时清空已加载的整包与文档目录，避免 @ 菜单串到上一题的课件
    watch(() => props.languagePackId, () => {
      coursewarePack.value = null
      coursewarePackLoaded.value = false
      coursewareDocuments.value = []
      coursewareDocumentsLoaded.value = false
      knowledgeComponentItems.value = []
      knowledgeComponentsLoaded.value = false
      knowledgeComponentsLoading.value = false
      knowledgeComponentsLoadPromise = null
      composer.handlers.refreshProvider('coursewares')
      composer.handlers.refreshProvider('courseware-pages')
      composer.handlers.refreshProvider('knowledge-components')
    })

    return {
      moRef: moRef,
      coursewarePack: coursewarePack,
      coursewarePackLoaded: coursewarePackLoaded,
      coursewarePackLoading: coursewarePackLoading,
      ensureCurrentCoursewarePackLoaded: ensureCurrentCoursewarePackLoaded,
      coursewareDocuments: coursewareDocuments,
      coursewareDocumentsLoaded: coursewareDocumentsLoaded,
      coursewareDocumentsLoading: coursewareDocumentsLoading,
      ensureCoursewareDocumentsLoaded: ensureCoursewareDocumentsLoaded,
      knowledgeComponentItems: knowledgeComponentItems,
      knowledgeComponentsLoaded: knowledgeComponentsLoaded,
      knowledgeComponentsLoading: knowledgeComponentsLoading,
      ensureKnowledgeComponentsLoaded: ensureKnowledgeComponentsLoaded,
      learnerNotebookItems: learnerNotebookItems,
      learnerNotebooksLoaded: learnerNotebooksLoaded,
      learnerNotebooksLoading: learnerNotebooksLoading,
      ensureLearnerNotebooksLoaded: ensureLearnerNotebooksLoaded,
      rawText: composer.rawText,
      atMenuVisible: composer.atMenuVisible,
      atQuery: composer.atQuery,
      atGroups: composer.atGroups,
      atActiveIndex: composer.atActiveIndex,
      slashMenuVisible: composer.slashMenuVisible,
      slashQuery: composer.slashQuery,
      slashGroups: composer.slashGroups,
      slashActiveIndex: composer.slashActiveIndex,
      composerHandlers: composer.handlers
    }
  },
  data () {
    return {
      feedbackMap: {},
      iconComponents: ICON_COMPONENTS,
      coursewarePreviewVisible: false,
      coursewarePreviewLoading: false,
      coursewarePreviewError: '',
      coursewarePreviewRefs: [],
      selectedCoursewareRef: null,
      coursewarePreviewPage: null,
      welcomeData: {},
      profileDrawerVisible: false
    }
  },
  computed: {
    loadingPhaseText () {
      const event = (this.studentEvent || '').toUpperCase()
      if (event.includes('READING') || event.includes('GUIDE')) return '正在分析题目...'
      if (event.includes('IDEATING') || event.includes('IDEATE')) return '正在分析你的思路...'
      if (event.includes('ERROR') || event.includes('DIAGNOS')) return '正在诊断代码错误...'
      if (event.includes('AC_REVIEW') || event.includes('POST_AC')) return '正在总结解题过程...'
      if (event.includes('TRANSFER')) return '正在寻找相似题目...'
      return '正在思考中...'
    },
    /**
     * 输入栏 / Welcome 区共享的快捷动作。
     * 拼装挑战已下沉到骨架代码卡片底部入口，由 {@link methods.isHiddenTutorAction} 统一隐藏。
     */
    filteredQuickActions () {
      const list = Array.isArray(this.quickActions) ? this.quickActions : []
      return list.filter(action => !this.isHiddenTutorAction(action))
    },
    effectiveWelcomeActions () {
      const ICON_MAP = {
        knowledge_review: 'Reading',
        problem_guide: 'Reading',
        error_chain: 'Warning'
      }
      if (this.welcomeData.starter_actions && this.welcomeData.starter_actions.length) {
        return this.welcomeData.starter_actions.map((item, i) => ({
          key: item.key || ('welcome_' + i),
          label: item.label || '',
          event: item.event || '',
          payload: item.payload || null,
          icon: ICON_MAP[item.key] || (i === 0 ? 'Reading' : 'Lightning')
        })).filter(action => !this.isHiddenTutorAction(action))
      }
      return this.filteredQuickActions
    },
    isApprovalState () {
      return this.runtimeContext && this.runtimeContext.runtimeState === 'WAITING_HUMAN_APPROVAL'
    },
    isRestoringState () {
      return this.runtimeContext && this.runtimeContext.runtimeState === 'RESTORING'
    },
    isFailedState () {
      return this.runtimeContext && this.runtimeContext.runtimeState === 'FAILED'
    },
    runtimeStatusVisible () {
      return this.isApprovalState || this.isRestoringState || this.isFailedState
    },
    approvalDescription () {
      const ACTION_LABELS = {
        confirm_scaffold: '确认插入代码骨架',
        confirm_transfer: '确认迁移练习',
        confirm_memory_save: '确认保存学习记忆',
        confirm_high_risk_tool_use: '确认高风险工具调用',
        confirm_retrieval_override: '确认检索覆盖'
      }
      return ACTION_LABELS[this.pendingHumanAction] || this.pendingHumanAction || '需要你的确认'
    },
    isInputBlocked () {
      return this.isApprovalState || this.isRestoringState
    },
    inputPlaceholder () {
      if (this.isApprovalState) return '请先处理审批操作'
      if (this.isRestoringState) return '正在恢复中，请稍候...'
      if (!this.canChatInput) return '当前阶段不允许继续对话'
      if (this.currentPlanStep && !this.planPaused && !this.planCompleted && !this.planSurrendered) {
        return '先提交你这一步的思考或样例预测...'
      }
      if (this.inputMode === 'ideate') return '描述你的解题思路...'
      return '输入消息与 AI 助手对话...'
    },
    coursewarePreviewFrameUrl () {
      if (!this.selectedCoursewareRef || !this.selectedCoursewareRef.document_id || !this.selectedCoursewareRef.page_no || !this.languagePackId) {
        return ''
      }
      if (this.coursewarePreviewPage && this.coursewarePreviewPage.preview_url) {
        return `${this.coursewarePreviewPage.preview_url}#page=${this.selectedCoursewareRef.page_no}`
      }
      return api.getLanguagePackQaPreviewUrl(this.languagePackId, this.selectedCoursewareRef.document_id, this.selectedCoursewareRef.page_no)
    },
    activeCoursewareMeta () {
      if (!this.selectedCoursewareRef) return '选择一条课件引用查看详情'
      const parts = []
      if (this.selectedCoursewareRef.document_title) {
        parts.push(this.selectedCoursewareRef.document_title)
      }
      if (this.selectedCoursewareRef.chapter) {
        parts.push(`第 ${this.selectedCoursewareRef.chapter} 章`)
      }
      const pageNo = this.selectedCoursewareRef.slide_number || this.selectedCoursewareRef.page_no
      if (pageNo) {
        parts.push(`P${pageNo}`)
      }
      return parts.join(' · ')
    },
    cardCharacter () {
      return (cardType) => {
        const charId = getCharacterForCardType(cardType)
        if (!charId) return null
        return getCharacter(charId)
      }
    },
    cardCharacterSprite () {
      return (cardType) => {
        const charId = getCharacterForCardType(cardType)
        if (!charId) return ''
        return getSpritePath(charId, getExpressionForEvent(charId, 'card_delivered'))
      }
    },
    cardCharStyle () {
      return (cardType) => {
        const char = this.cardCharacter(cardType)
        if (!char) return {}
        return { '--cc-accent': char.color, '--cc-bg': char.colorLight }
      }
    },
    encourageChar () {
      const ids = ['nene', 'yoshino', 'ayase', 'kanna', 'murasame']
      const pick = ids[Math.floor(Math.random() * ids.length)]
      return getCharacter(pick)
    },
    encourageCharSprite () {
      if (!this.encourageChar) return ''
      const exprMap = { nene: 'confused', yoshino: 'tsundere_pout', ayase: 'pout', kanna: 'contemplative', murasame: 'cold' }
      return getSpritePath(this.encourageChar.id, exprMap[this.encourageChar.id] || 'normal')
    },
    encourageCharLine () {
      if (!this.encourageChar) return ''
      const lines = {
        nene: '别着急，我们一起想想办法吧～',
        yoshino: '……卡住了？让我看看你的代码',
        ayase: '诶？还没过？没关系，再试试！',
        kanna: '……换个思路',
        murasame: '遇到瓶颈了？看看提示再来'
      }
      return lines[this.encourageChar.id] || lines.nene
    },
    currentPlanStep () {
      if (!this.planSteps || !this.planSteps.length) return null
      return this.planSteps.find(step => ['active', 'current', 'in_progress'].includes(String(step.status || '').toLowerCase())) ||
        this.planSteps.find(step => String(step.status || '').toLowerCase() === 'pending') ||
        this.planSteps[0]
    },
    timelineItems () {
      const messageItems = this.messages
        .filter(msg => msg && msg.type !== 'encouragement')
        .map(m => ({ ...m, _kind: 'message' }))
      if (messageItems.length === 0) return []

      const INTERNAL_CHECKPOINT_LABELS = new Set([
        'loop', 'input', '__start__', '__end__', 'branch',
        'router', 'condition', 'supervisor'
      ])
      const checkpointItems = this.checkpoints
        .filter(cp => {
          const label = String(cp.label || '').trim().toLowerCase()
          return label && !INTERNAL_CHECKPOINT_LABELS.has(label)
        })
        .map(cp => ({
          id: cp.checkpoint_id,
          timestamp: new Date(cp.created_at).getTime(),
          _kind: 'checkpoint',
          ...cp
        }))

      const items = [
        ...messageItems,
        ...checkpointItems
      ]
      return items.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0))
    }
  },
  watch: {
    timelineItems: {
      deep: true,
      handler () {
        this.syncMessageStreamToBottom()
      }
    },
    loading () {
      this.syncMessageStreamToBottom()
    },
    visible (val) {
      if (val) {
        this.syncMessageStreamToBottom()
        if (!this.welcomeData.greeting && this.problemId) {
          this.fetchWelcome()
        }
      }
    }
  },
  methods: {
    async fetchWelcome () {
      try {
        const res = await api.getTutorWelcome(this.problemId)
        if (res.data && res.data.data) {
          this.welcomeData = res.data.data
        }
      } catch {
        // 欢迎语失败不影响主流程，保留本地默认文案。
      }
    },
    syncMessageStreamToBottom () {
      this.$nextTick(() => {
        const el = this.$refs.messageStream
        if (el) el.scrollTop = el.scrollHeight
      })
    },
    submitFeedback (item, feedback) {
      const current = this.feedbackMap[item.id]
      if (current === feedback) return
      this.feedbackMap[item.id] = feedback
      const event = {
        event_type: 'agent_feedback',
        extra_data: {
          agent_id: item.agent_id || null,
          card_type: item.type,
          feedback: feedback,
          workflow_event_id: item.workflow_event_id || item.id
        }
      }
      this.$emit('report-event', event)
    },
    formatTime (isoStr) {
      if (!isoStr) return ''
      const d = new Date(isoStr)
      return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
    },
    renderMarkdown (text) {
      if (!text) return ''
      let html = sanitize(marked(text))
      html = html.replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
      return html
    },
    handleSend () {
      this.composerHandlers.submit()
    },
    handleCompactPlaceholder () {
      this.$emit('compact-session')
    },
    handlePlanConfirmStep (payload) {
      this.$emit('plan-confirm-step', payload)
    },
    handleQuickAction (action) {
      if (action.key === 'ideate') {
        this.$emit('switch-input-mode', 'ideate')
      } else {
        this.$emit('trigger-agent', action)
      }
    },
    isHiddenTutorAction (action) {
      const normalizedEvent = String((action && action.event) || '').toUpperCase()
      const normalizedKey = String((action && action.key) || '').trim().toLowerCase()
      const normalizedLabel = String((action && action.label) || '').trim()
      if (normalizedEvent === 'CODING' || normalizedKey === 'coding' || normalizedLabel === '开始编码' || normalizedLabel === '编码') return true
      // 拼装挑战已下沉到骨架代码卡片底部，独立快捷入口隐藏避免重复
      if (normalizedKey === 'parsons') return true
      return false
    },
    handleRequestVisualize () {
      this.$emit('request-visualize')
    },
    handleQuickQuestion (questionText) {
      if (!this.canChatInput) return
      this.composerHandlers.setText(questionText)
      this.$nextTick(() => this.composerHandlers.submit())
    },
    handleStartIdeate (warmupQuestion) {
      if (!this.canStartIdeate) return
      this.$emit('show-warmup', warmupQuestion)
    },
    handleRequestSkeleton () {
      if (!this.canRequestSkeleton) return
      this.$emit('request-skeleton')
    },
    isPreviewableCoursewareRef (ref) {
      return !!(ref && ref.document_id && ref.page_no)
    },
    coursewareRefKey (ref, index = 0) {
      if (!ref) return `courseware-${index}`
      return `${ref.document_id || 'doc'}-${ref.page_no || 'page'}-${index}`
    },
    isSelectedCoursewareRef (ref) {
      if (!this.selectedCoursewareRef || !ref) return false
      return String(this.selectedCoursewareRef.document_id) === String(ref.document_id) &&
        String(this.selectedCoursewareRef.page_no) === String(ref.page_no)
    },
    openCoursewareInNewTab (ref) {
      if (!ref || !ref.document_id || !this.languagePackId) return
      const url = api.getLanguagePackQaPreviewUrl(this.languagePackId, ref.document_id, ref.slide_number || ref.page_no)
      window.open(url, '_blank')
    },
    async handleOpenCoursewareRef (ref, refs = []) {
      if (!this.isPreviewableCoursewareRef(ref) || !this.languagePackId) return
      const previewableRefs = (refs || []).filter(this.isPreviewableCoursewareRef)
      this.coursewarePreviewRefs = previewableRefs.length ? previewableRefs : [ref]
      this.coursewarePreviewVisible = true
      await this.loadCoursewarePreview(ref)
    },
    async selectCoursewareRef (ref) {
      if (!this.isPreviewableCoursewareRef(ref) || this.isSelectedCoursewareRef(ref)) return
      await this.loadCoursewarePreview(ref)
    },
    async loadCoursewarePreview (ref) {
      this.selectedCoursewareRef = ref
      this.coursewarePreviewLoading = true
      this.coursewarePreviewError = ''
      try {
        this.coursewarePreviewPage = await fetchCoursewarePreviewPage(
          this.workflowQueryClient,
          this.languagePackId,
          ref.document_id,
          ref.page_no
        )
      } catch {
        this.coursewarePreviewPage = null
        this.coursewarePreviewError = '课件页加载失败'
        notify.error('课件页加载失败')
      } finally {
        this.coursewarePreviewLoading = false
      }
    },
    handleCoursewarePreviewClosed () {
      this.coursewarePreviewVisible = false
      this.coursewarePreviewLoading = false
      this.coursewarePreviewError = ''
      this.coursewarePreviewRefs = []
      this.selectedCoursewareRef = null
      this.coursewarePreviewPage = null
    },
    handleClearChat () {
      ElMessageBox.confirm(
        '确定要清空当前对话记录和学习记忆吗？此操作不可撤销。',
        '清空对话',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        this.$emit('clear-chat')
      }).catch(() => {})
    }
  }
}
</script>

<style lang="less" scoped>
.unified-panel {
  position: fixed;
  top: 64px;
  right: 0;
  bottom: 0;
  width: 420px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-left: 1px solid rgba(226, 232, 240, 0.6);
  box-shadow: -8px 0 32px rgba(31, 38, 135, 0.08);
  z-index: 100;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  background: rgba(248, 250, 252, 0.5);

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--primary-color);
  }
  .panel-title {
    font-weight: 600;
    font-size: 14px;
    color: var(--text-primary);
  }
  .header-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .clear-btn {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    cursor: pointer;
    color: var(--text-disabled);
    transition: all 0.15s;
    &:hover {
      background: rgba(254, 242, 242, 0.8);
      color: #EF4444;
    }
  }
  .profile-btn {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    cursor: pointer;
    color: var(--text-disabled);
    transition: all 0.15s;
    &:hover {
      background: var(--bg-panel);
      color: var(--text-secondary);
    }
  }
  .close-btn {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    cursor: pointer;
    color: var(--text-disabled);
    transition: all 0.15s;
    &:hover {
      background: var(--bg-panel);
      color: var(--text-secondary);
    }
  }
}

.plan-recommendation-banner,
.plan-area {
  margin: 12px 14px 0;
  border: 1px solid rgba(251, 191, 36, 0.28);
  background: linear-gradient(180deg, rgba(255, 251, 235, 0.96), rgba(255, 247, 237, 0.94));
  border-radius: 14px;
  padding: 12px;
  flex-shrink: 0;
}

.plan-recommendation-top,
.current-step-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.plan-recommendation-title,
.current-step-title {
  font-weight: 700;
  color: #7c2d12;
}

.plan-recommendation-reason,
.plan-recommendation-body,
.current-step-block,
.plan-reasoning {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.7;
  color: #7c2d12;
}

.plan-recommendation-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.current-step-card {
  margin-top: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(251, 191, 36, 0.22);
  padding: 12px;
}

.current-step-label,
.current-step-key {
  font-size: 11px;
  font-weight: 700;
  color: #92400e;
}

.current-step-role {
  font-size: 11px;
  color: #b45309;
}

.message-stream {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-stream > * {
  flex-shrink: 0;
}

.message-stream-spacer {
  flex: 1 0 auto;
  min-height: 0;
}

.timeline-item-shell {
  flex-shrink: 0;
}

.welcome-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
  padding: 32px 24px;
  text-align: center;

  .welcome-icon-wrap {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba(37, 99, 235, 0.08) 0%, rgba(99, 102, 241, 0.12) 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 16px;
    color: var(--primary-color);
  }

  .welcome-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 8px;
  }

  .welcome-desc {
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
    margin-bottom: 24px;
    max-width: 280px;
  }

  .welcome-memory-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 4px;
  }

  .welcome-tag {
    padding: 3px 10px;
    background: rgba(239,68,68,0.12);
    color: #fca5a5;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 500;
  }

  .welcome-actions {
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;
    max-width: 260px;
  }

  .welcome-action-chip {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 18px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.8);
    border: 1px solid rgba(219, 234, 254, 0.8);
    color: var(--primary-color);
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

    &:hover {
      background: #dbeafe;
      border-color: var(--primary-color);
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.12);
    }

    &:active {
      transform: scale(0.97);
    }
  }
}

.system-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 6px 12px;
  background: rgba(241, 245, 249, 0.6);
  border-radius: 8px;
  .system-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--primary-color);
    flex-shrink: 0;
  }
}

.user-msg {
  display: flex;
  justify-content: flex-end;
  .user-bubble {
    position: relative;
    max-width: 82%;
    padding: 10px 14px 10px 16px;
    background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 255, 0.96) 100%);
    color: #1e293b;
    border: 1px solid rgba(99, 102, 241, 0.18);
    border-radius: 15px 15px 5px 15px;
    font-size: 13px;
    line-height: 1.6;
    box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
    overflow: hidden;
    &::before {
      content: '';
      position: absolute;
      inset: 0 auto 0 0;
      width: 3px;
      background: linear-gradient(180deg, #8b5cf6 0%, #38bdf8 100%);
    }
  }
}

.ai-msg {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  .ai-avatar {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: #fff;
    border: 1px solid var(--border-color);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    color: var(--primary-color);
  }

  .ai-avatar-char {
    overflow: hidden;
    padding: 0;
  }

  .ai-avatar-sprite {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: top center;
  }

  .char-card-wrap {
    border-left: 3px solid var(--cc-accent, var(--primary-color));
    padding-left: 8px;
    margin-left: 4px;
  }

  .char-card-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
  }

  .char-card-avatar {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    object-fit: cover;
    object-position: top center;
  }

  .char-card-name {
    font-size: 12px;
    font-weight: 600;
  }
  .ai-bubble {
    max-width: 82%;
    padding: 10px 14px;
    background: rgba(255, 255, 255, 0.8);
    border: 1px solid var(--border-color);
    border-radius: 12px 12px 12px 4px;
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-primary);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
    :deep(p ) { margin-bottom: 6px; &:last-child { margin-bottom: 0; } }
    :deep(code ) {
      font-family: var(--font-mono);
      background: rgba(0, 0, 0, 0.05);
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 0.9em;
    }
    :deep(pre ) {
      background: rgba(248, 250, 252, 0.8);
      border: 1px solid var(--border-color);
      padding: 8px;
      border-radius: 6px;
      overflow-x: auto;
      margin: 6px 0;
      code { background: transparent; padding: 0; }
    }
  }
}

.error-msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 12.5px;
  color: #b91c1c;
  padding: 10px 14px;
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.85), rgba(254, 226, 226, 0.6));
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: 10px;
  line-height: 1.6;
  box-shadow: 0 1px 3px rgba(239, 68, 68, 0.06);
}

.agent-msg-fade-enter-active {
  animation: msg-slide-in 0.4s ease-out;
}
@keyframes msg-slide-in {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.loading-area {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 0 4px;
}

.loading-phase-text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
  padding: 4px 0;
}

.loading-skeleton {
  width: 100%;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  .wave-line {
    height: 10px;
    background: linear-gradient(90deg, #f1f5f9, #e2e8f0, #f1f5f9);
    background-size: 200% 100%;
    border-radius: 5px;
    animation: shimmer 1.5s infinite;
  }
}


.checkpoint-marker {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 2px 0;
  .checkpoint-line {
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, transparent, rgba(99, 102, 241, 0.3), transparent);
  }
  .checkpoint-label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: #6366F1;
    padding: 2px 8px;
    border-radius: 10px;
    background: rgba(238, 242, 255, 0.7);
    border: 1px solid rgba(199, 210, 254, 0.5);
    white-space: nowrap;
    transition: all 0.15s;
  }
  &:hover .checkpoint-label {
    background: rgba(224, 231, 255, 0.9);
    border-color: #6366F1;
  }
}

.msg-actions {
  display: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  color: var(--text-disabled);
  transition: all 0.15s;
  align-self: flex-end;
  margin-top: 4px;
  &:hover {
    color: var(--primary-color);
    background: rgba(239, 246, 255, 0.8);
  }
}

.ai-msg:hover .msg-actions {
  display: flex;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes stop-pulse {
  0%, 100% { box-shadow: 0 2px 6px rgba(239, 68, 68, 0.2); }
  50% { box-shadow: 0 2px 12px rgba(239, 68, 68, 0.4); }
}

.input-area {
  position: relative;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
  background: rgba(248, 250, 252, 0.5);

  .ideate-input-hint {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #D97706;
    margin-bottom: 8px;
    padding: 4px 8px;
    background: rgba(255, 251, 235, 0.8);
    border-radius: 6px;
  }

  .input-row {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    margin-bottom: 8px;
    .panel-input {
      flex: 1;
      :deep(textarea ) {
        border-radius: 10px !important;
        font-size: 13px;
        resize: none;
        background: rgba(255, 255, 255, 0.7);
        backdrop-filter: blur(4px);
        transition: border-color 0.2s, box-shadow 0.2s;
        &:focus {
          background: rgba(255, 255, 255, 0.95);
          border-color: var(--primary-color);
          box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
        }
      }
    }
    .action-btn {
      flex-shrink: 0;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      border: none;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      background: var(--primary-color);
      color: #fff;
      box-shadow: 0 2px 6px rgba(37, 99, 235, 0.15);

      &:hover:not(:disabled) {
        transform: scale(1.06);
        box-shadow: 0 3px 10px rgba(37, 99, 235, 0.25);
      }
      &:active:not(:disabled) {
        transform: scale(0.95);
      }
      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }
      &.is-stop {
        background: #EF4444;
        box-shadow: 0 2px 6px rgba(239, 68, 68, 0.2);
        animation: stop-pulse 2s infinite;
        &:hover {
          background: #DC2626;
          box-shadow: 0 3px 10px rgba(239, 68, 68, 0.3);
        }
      }
    }
  }

  .reference-suggestions {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 132px;
    overflow-y: auto;
    margin: -3px 42px 6px 0;
    padding: 6px;
    border-radius: 10px;
    border: 1px solid rgba(37, 99, 235, 0.14);
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.1);
  }

  .reference-suggestion-item {
    min-height: 34px;
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 1px 8px;
    align-items: center;
    width: 100%;
    border: 0;
    border-radius: 8px;
    padding: 5px 7px;
    background: transparent;
    text-align: left;
    cursor: pointer;
    transition: background 0.16s ease;

    &:hover {
      background: rgba(239, 246, 255, 0.88);
    }
  }

  .reference-suggestion-token {
    grid-row: span 2;
    font-family: 'Fira Code', monospace;
    font-size: 10px;
    color: var(--primary-color);
    background: rgba(219, 234, 254, 0.68);
    border-radius: 999px;
    padding: 2px 6px;
  }

  .reference-suggestion-main {
    font-size: 12px;
    line-height: 1.2;
    font-weight: 600;
    color: #1f2937;
  }

  .reference-suggestion-desc {
    font-size: 11px;
    line-height: 1.25;
    color: #64748b;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  .quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 12px;
    min-height: 26px;
    a {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      color: var(--primary-color);
      cursor: pointer;
      padding: 5px 12px;
      border-radius: 14px;
      background: rgba(239, 246, 255, 0.7);
      border: 1px solid rgba(219, 234, 254, 0.6);
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      font-weight: 500;
      &:hover {
        background: #dbeafe;
        border-color: var(--primary-color);
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(37, 99, 235, 0.12);
      }
      &:active {
        transform: scale(0.96);
      }
    }
  }
}

.runtime-status-area {
  flex-shrink: 0;
  padding: 0 16px;
}

.runtime-banner {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  margin-top: 12px;
}

.runtime-banner-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.runtime-banner-body {
  flex: 1;
  min-width: 0;
}

.runtime-banner-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.runtime-banner-desc {
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
  word-break: break-word;
}

.runtime-banner-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.runtime-action-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 6px;
  border: none;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.runtime-action-btn:hover {
  transform: translateY(-1px);
}

.runtime-action-btn:active {
  transform: scale(0.97);
}

.runtime-banner-approval {
  background: rgba(245, 158, 11, 0.06);
  border: 1px solid rgba(245, 158, 11, 0.2);
  .runtime-banner-icon {
    background: rgba(245, 158, 11, 0.1);
    color: #D97706;
  }
  .runtime-banner-title { color: #92400E; }
  .runtime-banner-desc { color: #B45309; }
}

.runtime-action-confirm {
  background: #10B981;
  color: #fff;
  &:hover { background: #059669; }
}

.runtime-action-reject {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
  &:hover {
    background: rgba(239, 68, 68, 0.15);
    border-color: #EF4444;
  }
}

.runtime-banner-restoring {
  background: rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.2);
  .runtime-banner-icon {
    background: rgba(99, 102, 241, 0.1);
    color: #6366F1;
  }
  .runtime-banner-title { color: #4338CA; }
  .runtime-banner-desc { color: #6366F1; font-family: var(--font-mono); font-size: 11px; }
}

.runtime-spin {
  animation: runtime-spin-anim 1s linear infinite;
}

@keyframes runtime-spin-anim {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.runtime-banner-failed {
  background: rgba(239, 68, 68, 0.05);
  border: 1px solid rgba(239, 68, 68, 0.18);
  .runtime-banner-icon {
    background: rgba(239, 68, 68, 0.1);
    color: #EF4444;
  }
  .runtime-banner-title { color: #991B1B; }
  .runtime-banner-desc { color: #B91C1C; }
}

.runtime-action-restore {
  background: var(--primary-color);
  color: #fff;
  &:hover { background: var(--primary-hover, #1d4ed8); }
}

.runtime-action-restart {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
  &:hover {
    background: rgba(239, 68, 68, 0.15);
    border-color: #EF4444;
  }
}

.quick-actions a.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
  pointer-events: none;
}

.slide-panel-enter-active,
.slide-panel-leave-active {
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-panel-enter,
.slide-panel-leave-to {
  transform: translateX(100%);
}

/* ── Agent 反馈按钮 ── */
.agent-feedback-row {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}
.fb-btn {
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(0, 0, 0, 0.02);
  font-size: 11.5px;
  color: #94a3b8;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  transition: all 0.18s;
  user-select: none;
  outline: none;
  font-family: inherit;
  &:hover {
    color: #475569;
    background: rgba(0, 0, 0, 0.04);
    border-color: rgba(0, 0, 0, 0.1);
  }
  &:focus-visible {
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
  }
  &.fb-active {
    color: #1e293b;
    background: rgba(37, 99, 235, 0.06);
    border-color: rgba(37, 99, 235, 0.15);
  }
}

.courseware-preview-dialog {
  :deep(.el-dialog) {
    max-width: 1080px;
    border-radius: 18px;
    overflow: hidden;
  }

  :deep(.el-dialog__body) {
    padding-top: 8px;
  }
}

.courseware-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.courseware-dialog-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--primary-color);
}

.courseware-dialog-title-group {
  min-width: 0;
}

.courseware-dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.courseware-dialog-meta {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.courseware-dialog-open-link {
  flex-shrink: 0;
  color: var(--primary-color);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.courseware-dialog-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  min-height: 520px;
}

.courseware-dialog-sidebar {
  padding-right: 6px;
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  overflow-y: auto;
}

.courseware-dialog-sidebar-title {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
}

.courseware-dialog-ref-item {
  width: 100%;
  margin-bottom: 10px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: #fff;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.courseware-dialog-ref-item:hover,
.courseware-dialog-ref-item.is-active {
  border-color: rgba(37, 99, 235, 0.35);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.08);
}

.courseware-dialog-ref-item.is-active {
  background: rgba(37, 99, 235, 0.04);
}

.courseware-dialog-ref-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.courseware-dialog-ref-doc {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.courseware-dialog-ref-page {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--primary-color);
}

.courseware-dialog-ref-preview {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.courseware-dialog-main {
  min-width: 0;
}

.courseware-dialog-loading,
.courseware-dialog-error {
  padding: 12px 0;
}

.courseware-dialog-error {
  color: #dc2626;
  font-size: 13px;
  font-weight: 600;
}

.courseware-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.courseware-dialog-frame-wrap {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  overflow: hidden;
  background: #f8fafc;
}

.courseware-dialog-iframe {
  width: 100%;
  height: 420px;
  display: block;
  border: none;
  background: #fff;
}

.courseware-dialog-text-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.courseware-dialog-text-card {
  padding: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.7);
}

.courseware-dialog-text-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.courseware-dialog-text-body {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .courseware-dialog-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .courseware-dialog-sidebar {
    max-height: 220px;
    padding-right: 0;
    padding-bottom: 12px;
    border-right: none;
    border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  }

  .courseware-dialog-iframe {
    height: 320px;
  }

  .courseware-dialog-text-grid {
    grid-template-columns: 1fr;
  }
}
</style>
