<template>
  <div class="qa-page">
    <div class="qa-shell" :class="{ 'evidence-visible': showEvidencePanel }">
      <aside class="qa-sidebar qa-card">
        <div class="qa-pane-head">
          <div>
            <div class="qa-eyebrow">Courseware AI</div>
            <h1>课件问答助手</h1>
          </div>
          <button class="qa-ghost-btn" type="button" @click="reloadAll" :disabled="isBusy">
            <ElIcon><RefreshRight /></ElIcon>
            刷新
          </button>
        </div>

        <div class="qa-pack-section">
          <label class="qa-section-label" for="qa-pack-select">当前课程内容包</label>
          <select
            id="qa-pack-select"
            class="qa-pack-select"
            :value="selectedLanguagePackId || ''"
            :disabled="loadings.packs || !packs.length || qaInputDisabled"
            @change="handlePackSelect"
          >
            <option v-if="!packs.length" value="" disabled>暂无可见课程内容包</option>
            <option v-for="pack in packs" :key="pack.id" :value="pack.id">{{ packOptionLabel(pack) }}</option>
          </select>
          <p v-if="currentPack" class="qa-pack-meta">
            {{ currentPack.primary_language }} · {{ currentPack.page_count || 0 }} 页
          </p>
          <p v-if="currentPack && !currentPackIsQaReady" class="qa-pack-warning">
            这个课程内容包已经对你可见，但尚未完成问答索引。
          </p>
        </div>

        <div class="qa-session-head">
          <span class="qa-section-label">会话历史</span>
          <button class="qa-primary-btn qa-primary-btn-small" type="button" @click="startNewSession" :disabled="!currentPackIsQaReady || isBusy || qaInputDisabled">
            新会话
          </button>
        </div>

        <div v-if="loadings.sessions" class="qa-session-skeleton">
          <div v-for="n in 4" :key="n" class="qa-skeleton-line"></div>
        </div>
        <div v-else-if="currentPack && !currentPackIsQaReady" class="qa-empty-inline qa-empty-inline-warning">
          当前课程内容包尚未完成问答索引，暂时不能创建会话。
        </div>
        <div v-else-if="sessions.length" class="qa-session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="qa-session-item"
            :class="{ 'is-active': session.id === activeSessionId, 'is-starred': session.starred, 'is-forked': !!session.parent_session_id }"
            :style="session.parent_session_id ? { paddingLeft: '32px' } : {}"
            @click="activateSession(session.id)"
          >
            <span v-if="session.parent_session_id" class="qa-session-fork-prefix">↳</span>
            <img v-if="currentCharacter" :src="currentCharSpriteSrc" class="qa-session-avatar" :alt="currentCharacter.name" />
            <span class="qa-session-title">{{ sessionTitle(session) }}</span>
            <span class="qa-session-actions" @click.stop>
              <button
                class="qa-session-action-btn"
                :class="{ 'is-starred': session.starred }"
                type="button"
                :title="session.starred ? '取消收藏' : '收藏'"
                @click="toggleStarSession(session)"
              >
                <ElIcon><StarFilled v-if="session.starred" /><Star v-else /></ElIcon>
              </button>
              <button
                class="qa-session-action-btn is-danger"
                type="button"
                title="删除会话"
                @click="confirmDeleteSession(session)"
              >
                <ElIcon><Delete /></ElIcon>
              </button>
            </span>
          </div>
        </div>
        <div v-else class="qa-empty-inline">当前课程内容包还没有问答记录。</div>
      </aside>

      <main class="qa-main qa-card">
        <div class="qa-main-head">
          <div class="qa-main-head-left">
            <div v-if="currentCharacter" class="qa-head-char" :style="{ borderColor: currentCharacter.color }">
              <img :src="currentCharSpriteSrc" :alt="currentCharacter.name" class="qa-head-char-img" />
            </div>
            <div>
              <div class="qa-eyebrow" v-if="currentCharacter">
                <span :style="{ color: currentCharacter.color }">{{ currentCharacter.name }}</span>
                <span class="qa-eyebrow-sep">·</span>
                <span>{{ currentCharacter.role }}</span>
              </div>
              <div class="qa-eyebrow" v-else>Courseware AI</div>
              <h2>{{ currentPack ? currentPack.name : '选择课程内容包开始问答' }}</h2>
              <p>{{ qaIntroText }}</p>
            </div>
          </div>
          <div v-if="qaAvailabilityState !== 'ready'" class="qa-status-pill" :class="qaAvailabilityStateClass">
            <span>{{ qaAvailabilityLabel }}</span>
          </div>
        </div>

        <div v-if="loadings.packs" class="qa-main-empty">
          <div class="qa-big-skeleton"></div>
          <div class="qa-big-skeleton short"></div>
        </div>
        <div v-else-if="!packs.length" class="qa-main-empty">
          <ElIcon class="qa-empty-icon"><CircleCloseFilled /></ElIcon>
          <h3>当前没有可见课程内容包</h3>
          <p>请先确认你已经加入带有已发布课程内容包的课堂，或当前账号具备相应的查看权限。</p>
        </div>
        <div v-else-if="currentPack && !currentPackIsQaReady" class="qa-main-empty">
          <ElIcon class="qa-empty-icon"><Warning /></ElIcon>
          <h3>当前课程内容包暂不可问答</h3>
          <p>这个课程内容包已经发布，但还没有完成页预览和 embedding 建立，所以这里先不开放课件问答。</p>
        </div>
        <template v-else>
          <div v-if="loadings.messages" class="qa-message-skeleton">
            <div v-for="n in 3" :key="n" class="qa-bubble-skeleton"></div>
          </div>
          <div v-else-if="messages.length" ref="messageList" class="qa-message-list">
            <div
              v-for="message in messages"
              :key="message.id"
              class="qa-message"
              :class="message.role === 'assistant' ? 'is-assistant' : 'is-user'"
            >
              <div v-if="message.role === 'assistant'" class="qa-avatar qa-avatar-char" :style="{ borderColor: currentCharacter ? currentCharacter.color : '' }">
                <img :src="currentCharSpriteSrc" :alt="currentCharacter ? currentCharacter.name : 'AI'" class="qa-avatar-sprite" />
              </div>
              <div v-else class="qa-avatar">
                <img v-if="userAvatarUrl" :src="userAvatarUrl" alt="我" class="qa-user-avatar-img" />
                <span v-else>我</span>
              </div>
              <div class="qa-bubble" :style="message.role === 'assistant' && currentCharacter ? { borderColor: currentCharacter.color + '40' } : {}">
                <div class="qa-message-role">
                  <span v-if="message.role === 'assistant' && currentCharacter" :style="{ color: currentCharacter.color }">{{ currentCharacter.name }}</span>
                  <span v-else-if="message.role === 'assistant'">课件问答助手</span>
                  <span v-else>我的问题</span>
                  <span v-if="message.role === 'assistant' && currentCharacter" class="qa-char-role-tag">{{ currentCharacter.role }}</span>
                </div>
                <div v-if="message.role === 'assistant'" class="qa-answer-block">
                  <div class="qa-answer-text qa-answer-markdown" v-html="renderMarkdown(resolveAnswerText(message))"></div>
                  <div v-if="isRefusalMessage(message)" class="qa-answer-state is-refusal">
                    证据不足，已拒答
                  </div>
                  <div v-else-if="isGroundedMessage(message)" class="qa-answer-state">
                    已定位到课件页证据
                  </div>

                  <div v-if="resolveCitations(message).length" class="qa-citation-list">
                    <button
                      v-for="citation in resolveCitations(message)"
                      :key="`${message.id}-${citation.document_id}-${citation.page_no}`"
                      class="qa-citation-chip"
                      type="button"
                      @click="openCitation(citation)"
                    >
                      <ElIcon><Document /></ElIcon>
                      {{ citation.document_title }} · 第 {{ citation.page_no }} 页
                    </button>
                  </div>

                  <div class="qa-feedback-row">
                    <button
                      class="qa-feedback-btn"
                      type="button"
                      :class="{ 'is-selected': feedbackByMessageId[message.id] === 'helpful' }"
                      @click="submitFeedback(message.id, 'helpful')"
                    >
                      <ElIcon><Star /></ElIcon>
                      有帮助
                    </button>
                    <button
                      class="qa-feedback-btn"
                      type="button"
                      :class="{ 'is-selected': feedbackByMessageId[message.id] === 'unhelpful' }"
                      @click="submitFeedback(message.id, 'unhelpful')"
                    >
                      <ElIcon><Warning /></ElIcon>
                      没帮助
                    </button>
                    <button
                      class="qa-feedback-btn"
                      type="button"
                      :class="{ 'is-selected': feedbackByMessageId[message.id] === 'citation_incorrect' }"
                      @click="submitFeedback(message.id, 'citation_incorrect')"
                    >
                      <ElIcon><Connection /></ElIcon>
                      引用不准
                    </button>
                  </div>

                  <div v-if="isAdmin && !isRefusalMessage(message) && resolveCitations(message).length && resolveVideoJob(message)" class="qa-video-row">
                    <div class="qa-video-status" :class="'is-' + resolveVideoJob(message).status">
                      <span v-if="resolveVideoJob(message).status === 'completed'" class="qa-video-link" @click="openVideo(resolveVideoJob(message))">
                        <ElIcon><VideoPlay /></ElIcon>
                        查看视频 ({{ resolveVideoJob(message).duration_seconds }}s)
                      </span>
                      <span v-else-if="resolveVideoJob(message).status === 'failed'" class="qa-video-failed">
                        生成失败
                      </span>
                      <span v-else class="qa-video-progress">
                        <ElIcon><Loading /></ElIcon>
                        {{ resolveVideoJob(message).status === 'queued' ? '排队中' : '生成中' }} {{ resolveVideoJob(message).progress_percent }}%
                      </span>
                    </div>
                  </div>
                </div>
                <div v-else class="qa-question-text">{{ message.content }}</div>
              </div>
            </div>
          </div>
          <div v-else class="qa-welcome-scene">
            <div class="qa-welcome-char-area">
              <img v-if="currentCharacter" :src="currentCharSpriteSrc" :alt="currentCharacter.name" class="qa-welcome-sprite" />
              <div class="qa-welcome-bubble" v-if="currentCharacter">
                <span class="qa-welcome-name" :style="{ color: currentCharacter.color }">{{ currentCharacter.name }}</span>
                <p class="qa-welcome-text">{{ charWelcomeText }}</p>
              </div>
            </div>
            <div class="qa-suggest-section">
              <div class="qa-suggest-label">试着问我：</div>
              <div class="qa-suggest-chips">
                <button
                  v-for="(q, qi) in suggestedQuestions"
                  :key="qi"
                  class="qa-suggest-chip"
                  type="button"
                  :style="currentCharacter ? { borderColor: currentCharacter.color + '40' } : {}"
                  @click="askSuggested(q)"
                >
                  {{ q }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="qaRuntimeStatusVisible" class="qa-runtime-status">
            <div v-if="qaRuntimeContext.runtimeState === 'QUEUED'" class="qa-runtime-banner is-queued">
              <ElIcon class="qa-runtime-spin"><Loading /></ElIcon>
              <span>问题已提交，排队中...</span>
            </div>
            <div v-else-if="qaRuntimeContext.runtimeState === 'RUNNING'" class="qa-runtime-banner is-running">
              <ElIcon class="qa-runtime-spin"><Loading /></ElIcon>
              <span>正在检索课件并生成回答...</span>
            </div>
            <div v-else-if="qaRuntimeContext.runtimeState === 'FAILED'" class="qa-runtime-banner is-failed">
              <ElIcon><Warning /></ElIcon>
              <div class="qa-runtime-failed-body">
                <span>{{ qaRuntimeContext.lastError || '回答生成失败' }}</span>
                <span v-if="qaRuntimeContext.failureBucket" class="qa-failure-bucket">{{ qaRuntimeContext.failureBucket }}</span>
                <button class="qa-retry-btn" type="button" @click="retryLastQuestion">
                  <ElIcon><RefreshRight /></ElIcon>
                  用原问题重试
                </button>
              </div>
            </div>
            <div v-else-if="qaRuntimeContext.runtimeState === 'EXPIRED'" class="qa-runtime-banner is-failed">
              <ElIcon><Warning /></ElIcon>
              <span>任务已超时，请重新发送</span>
            </div>
          </div>

          <div class="qa-composer">
            <ContextUsageBar
              v-if="qaContextUsage && qaContextUsage.tokens_limit"
              :tokens-used="qaContextUsage.tokens_used || 0"
              :tokens-limit="qaContextUsage.tokens_limit || 0"
              :model-name="qaContextUsage.model_name || ''"
              @compact-click="handleQaCompactPlaceholder"
            />

            <AtMentionMenu
              :visible="atMenuVisible"
              :groups="atGroups"
              :active-index="atActiveIndex"
              @select="composerHandlers.selectAtItem"
            />

            <SlashCommandMenu
              :visible="slashMenuVisible"
              :groups="slashGroups"
              :active-index="slashActiveIndex"
              @select="composerHandlers.selectSlashItem"
            />

            <el-input
              :model-value="rawText"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              resize="none"
              :placeholder="composerPlaceholder"
              :disabled="qaInputDisabled"
              @update:model-value="composerHandlers.onInput"
              @keydown="composerHandlers.onKeydown"
            />
            <div class="qa-composer-actions">
              <ComposerHintBar
                :at-active="atMenuVisible"
                :slash-active="slashMenuVisible"
              />
              <button class="qa-primary-btn" type="button" @click="composerHandlers.submit" :disabled="!canSend">
                {{ loadings.sending ? '发送中...' : '发送问题' }}
              </button>
            </div>
          </div>
        </template>
      </main>

      <aside v-if="showEvidencePanel" class="qa-evidence qa-card">
        <div class="qa-pane-head qa-pane-head-tight">
          <div>
            <div class="qa-eyebrow">{{ activeVideoPlayback ? 'Video Playback' : 'Evidence Preview' }}</div>
            <h3>{{ activeVideoPlayback ? '视频播放' : '证据侧栏' }}</h3>
          </div>
          <button class="qa-ghost-btn" type="button" @click="closeEvidencePanel">
            <ElIcon><Close /></ElIcon>
            收起
          </button>
        </div>

        <div v-if="activeVideoPlayback" class="qa-video-player-wrap">
          <video
            controls
            :src="activeVideoPlayback.video_path"
            :poster="activeVideoPlayback.poster_path || ''"
            class="qa-video-player"
            preload="metadata"
          ></video>
          <div class="qa-video-meta">
            时长 {{ activeVideoPlayback.duration_seconds }} 秒
          </div>
        </div>

        <div v-if="loadings.citation" class="qa-main-empty qa-main-empty-compact">
          <div class="qa-big-skeleton"></div>
          <div class="qa-big-skeleton short"></div>
        </div>
        <div v-else-if="citationPreview" class="qa-evidence-body">
          <div class="qa-evidence-card">
            <div class="qa-evidence-title">{{ citationPreview.document_title }}</div>
            <div class="qa-evidence-meta">第 {{ citationPreview.page_no }} 页</div>
          </div>

          <div class="qa-preview-frame-wrap">
            <PdfPageViewer
              :src="citationPreview.preview_url"
              :page="citationPreview.page_no"
            />
            <a class="qa-preview-open-btn" :href="viewerPageUrl" target="_blank" rel="noopener noreferrer">
              <ElIcon><Document /></ElIcon>
              在新标签页查看
            </a>
          </div>

        </div>
      </aside>
    </div>
    <MotionOverlay ref="moRef" />
  </div>
</template>

<script>
  import { computed, getCurrentInstance, defineAsyncComponent } from 'vue'
  import api from '@oj/api'
  import { notify } from '@/utils/notifications'
  import { checkInputSequence } from '@oj/utils/inputValidator'
  const MotionOverlay = defineAsyncComponent(() => import('@oj/components/MotionOverlay.vue'))
  import { ElMessageBox } from 'element-plus'
  import { mapGetters } from 'vuex'
  import { matchCharacterForQuestion } from './qaCharacterMatcher'
  import { getCharacter, getSpritePath, getExpressionForEvent } from '../problem/characterConfig'
  import { parseReferences } from '../problem/useReferenceParse'
  import PdfPageViewer from '@/components/PdfPageViewer.vue'
  import { useChatComposer } from '@oj/components/chat/useChatComposer'
  import AtMentionMenu from '@oj/components/chat/AtMentionMenu.vue'
  import SlashCommandMenu from '@oj/components/chat/SlashCommandMenu.vue'
  import ComposerHintBar from '@oj/components/chat/ComposerHintBar.vue'
  import ContextUsageBar from '@oj/components/chat/ContextUsageBar.vue'

  function encodeQaCtx (packId, sessionId) {
    const obj = {}
    if (packId) obj.p = String(packId)
    if (sessionId) obj.s = String(sessionId)
    return btoa(JSON.stringify(obj))
  }

  function decodeQaCtx (ctx) {
    try {
      return JSON.parse(atob(ctx || ''))
    } catch (_) {
      return {}
    }
  }
  import marked from 'marked'
  import { sanitize } from '@/utils/sanitize'
  import { buildWebSocketUrl, buildQaWebSocketPath } from '@/utils/websocketUrl'
  import {
    normalizeRuntimeEvent,
    assertAllowedForQaPage,
    SERVER_EVENTS
  } from '@/utils/runtimeContract'
  import {
    RefreshRight,
    Document,
    Connection,
    CircleCloseFilled,
    Warning,
    Star,
    StarFilled,
    Delete,
    VideoPlay,
    Loading,
    Close
  } from '@element-plus/icons-vue'

  const shortOjVerdictPattern = /\b(ac|wa|tle|mle|ce|re)\b/i
  const ojStrongTerms = [
    'oj', '题目编号', 'problem id', 'submission', 'judge', '判题', '测试点',
    '样例输入', '样例输出', '输入描述', '输出描述', 'sample input', 'sample output',
    'time limit', 'memory limit', '运行错误', '编译错误', '超时', '内存超限'
  ]
  const ojProblemTerms = ['这道题', '这题', '题目', '解题', '题解', '思路', '提交', '样例', '输入', '输出']
  const ojSolvingTerms = ['怎么写', '怎么做', '帮我写', '给我代码', '完整代码', '直接给答案', '答案', '通过不了', '过不了', '卡住了']
  const ojQuestionGuardMessage = '不要在这里问 OJ 题目、提交结果或索要完整解法。请回到题目页 AI 面板提问。'

  export default {
    name: 'LanguagePackQaPage',
    components: {
      PdfPageViewer,
      AtMentionMenu,
      SlashCommandMenu,
      ComposerHintBar,
      ContextUsageBar,
      MotionOverlay,
      RefreshRight,
      Document,
      Connection,
      CircleCloseFilled,
      Warning,
      Star,
      StarFilled,
      Delete,
      VideoPlay,
      Loading,
      Close
    },
    setup () {
      const instance = getCurrentInstance()
      const proxy = instance && instance.proxy
      const scopeKey = computed(() => {
        const packId = proxy && proxy.selectedLanguagePackId ? proxy.selectedLanguagePackId : 'none'
        const sessionId = proxy && proxy.activeSessionId ? proxy.activeSessionId : 'new'
        return `qa:${packId}:${sessionId}`
      })
      const isInputBlocked = computed(() => Boolean(proxy && proxy.qaInputDisabled))
      const atProviders = [
        {
          key: 'qa-pages',
          group: '课件页码',
          lazyLoad: true,
          items: () => proxy ? proxy.buildQaPageMentionItems() : []
        },
        {
          key: 'qa-kcs',
          group: '知识点',
          lazyLoad: true,
          maxInitialDisplay: 8,
          items: () => proxy ? proxy.buildQaKcMentionItems() : []
        },
        {
          key: 'qa-notebooks',
          group: '学习笔记',
          lazyLoad: true,
          maxInitialDisplay: 6,
          items: () => proxy ? proxy.buildQaNotebookMentionItems() : []
        }
      ]
      const slashCommands = [
        {
          key: 'qa-refs',
          group: '课件问答',
          command: '/refs',
          label: '证据侧栏',
          hint: '收起当前证据侧栏',
          run: () => {
            if (proxy && proxy.showEvidencePanel) {
              proxy.closeEvidencePanel()
            } else {
              notify.info('当前还没有可展示的证据页')
            }
          }
        },
        {
          key: 'qa-page',
          group: '课件问答',
          command: '/page',
          label: '跳转页码',
          hint: '/page <n>',
          run: ({ args }) => proxy && proxy.jumpToQaPage(args)
        },
        {
          key: 'qa-clear',
          group: '会话控制',
          command: '/clear',
          label: '收起证据',
          run: () => proxy && proxy.closeEvidencePanel()
        },
        {
          key: 'qa-export',
          group: '会话控制',
          command: '/export',
          label: '导出 Markdown',
          run: () => proxy && proxy.exportConversationMarkdown()
        },
        {
          key: 'qa-compact',
          group: '会话进阶',
          command: '/compact',
          label: '压缩上下文',
          status: 'available',
          run: () => proxy && proxy.handleCompactSession()
        },
        {
          key: 'qa-fork',
          group: '会话进阶',
          command: '/fork',
          label: '分叉会话',
          status: 'available',
          run: () => proxy && proxy.handleForkSession()
        }
      ]
      const composer = useChatComposer({
        scopeKey,
        atProviders,
        slashCommands,
        isInputBlocked,
        onSubmit: (text) => proxy && proxy.sendQuestion(text)
      })
      return {
        rawText: composer.rawText,
        atMenuVisible: composer.atMenuVisible,
        atGroups: composer.atGroups,
        atActiveIndex: composer.atActiveIndex,
        slashMenuVisible: composer.slashMenuVisible,
        slashGroups: composer.slashGroups,
        slashActiveIndex: composer.slashActiveIndex,
        composerHandlers: composer.handlers
      }
    },
    data () {
      return {
        packs: [],
        sessions: [],
        messages: [],
        selectedLanguagePackId: null,
        activeSessionId: null,
        qaContextUsage: { tokens_used: 0, tokens_limit: 0, model_name: '', last_updated: null },
        activeCitation: null,
        citationPreview: null,
        feedbackByMessageId: {},
        videoGenerating: {},
        activeVideoPlayback: null,
        videoPollingTimers: {},
        isAdmin: false,
        ojQuestionGuardMessage,
        qaRuntimeContext: {
          sessionId: null,
          taskId: null,
          runtimeState: null,
          serverEvent: null,
          failureBucket: null,
          lastError: null,
          updatedAt: null
        },
        qaPendingQuestion: '',
        _qaWsConnection: null,
        _qaWsReconnectTimer: null,
        _qaMentionCache: { packId: null, pages: null, kcs: null, notebooks: null },
        loadings: {
          packs: false,
          sessions: false,
          messages: false,
          sending: false,
          citation: false
        },
        qaAvailabilityState: 'loading',
        qaCharacterId: 'nene',
        qaCharExpression: ''
      }
    },
    computed: {
      ...mapGetters(['profile']),
      userAvatarUrl () {
        return this.profile && this.profile.avatar ? this.profile.avatar : ''
      },
      currentPack () {
        return this.resolvePackById(this.$data.selectedLanguagePackId)
      },
      canSend () {
        return Boolean(this.selectedLanguagePackId && this.activeSessionId && this.rawText.trim() && !this.loadings.sending && !this.qaInputDisabled)
      },
      isBusy () {
        return this.loadings.sessions || this.loadings.messages || this.loadings.sending
      },
      qaRuntimeStatusVisible () {
        const state = this.qaRuntimeContext && this.qaRuntimeContext.runtimeState
        return state === 'QUEUED' || state === 'RUNNING' || state === 'FAILED' || state === 'EXPIRED'
      },
      qaInputDisabled () {
        const state = this.qaRuntimeContext && this.qaRuntimeContext.runtimeState
        return state === 'QUEUED' || state === 'RUNNING'
      },
      previewFrameUrl () {
        if (!this.citationPreview) return ''
        return `${this.citationPreview.preview_url}#page=${this.citationPreview.page_no}`
      },
      viewerPageUrl () {
        if (!this.citationPreview) return ''
        const params = new URLSearchParams({
          url: this.citationPreview.preview_url,
          page: String(this.citationPreview.page_no),
          title: this.citationPreview.document_title || ''
        })
        return `/language-pack-qa/viewer?${params.toString()}`
      },
      currentPackIsQaReady () {
        return Boolean(this.currentPack && this.currentPack.qa_ready)
      },
      currentCharacter () {
        return getCharacter(this.qaCharacterId)
      },
      charWelcomeText () {
        const texts = {
          nene: '你好呀～有什么关于课件的问题都可以问我，我会帮你从课件中找到答案呢',
          yoshino: '有问题就直接问。我会从课件中精确定位相关内容给你',
          ayase: '嘿！想问什么就问吧！我帮你从课件里找答案！',
          kanna: '……问吧。课件里的内容，我都知道',
          murasame: '说吧，什么问题。别浪费时间'
        }
        return texts[this.qaCharacterId] || texts.nene
      },
      suggestedQuestions () {
        const packLang = this.currentPack && this.currentPack.primary_language
        if (packLang && packLang.toLowerCase().includes('python')) {
          return ['变量是什么？', '列表怎么用？', 'for 循环的语法是什么？', '函数怎么定义？']
        }
        if (packLang && packLang.toLowerCase().includes('c')) {
          return ['指针是什么？', '数组怎么声明？', 'struct 怎么用？', '如何分配内存？']
        }
        return ['这门课的核心概念有哪些？', '最重要的知识点是什么？', '有哪些常见的易错点？', '课件第一章讲了什么？']
      },
      currentCharSpriteSrc () {
        const expr = this.qaCharExpression || getExpressionForEvent(this.qaCharacterId, this.loadings.sending ? 'thinking' : 'idle')
        return getSpritePath(this.qaCharacterId, expr)
      },
      qaAvailabilityLabel () {
        if (this.qaAvailabilityState === 'empty') return '暂无可见课程内容包'
        if (this.qaAvailabilityState === 'unready') return '当前课程内容包暂不可问答'
        if (this.qaAvailabilityState === 'ready') return '已进入课件问答模式'
        return '加载中'
      },
      qaIntroText () {
        const lang = this.currentPack ? this.currentPack.primary_language : '编程'
        if (!this.currentPack) {
          return `可以提问${lang}知识或日常问题，课件有涉及时会引用对应页码。`
        }
        if (!this.currentPackIsQaReady) {
          return '你现在选中的是一个已发布但尚未完成问答索引的课程内容包，所以这里先不会开放提问。'
        }
        return `可以提问${lang}知识或日常问题，课件有涉及时会引用对应页码。`
      },
      qaAvailabilityStateClass () {
        return `is-${this.qaAvailabilityState}`
      },
      composerPlaceholder () {
        const lang = this.currentPack ? this.currentPack.primary_language : '编程'
        return `可以问编程知识、${lang} 问题或日常聊天，课件有涉及时会引用页码`
      },
      showEvidencePanel () {
        return Boolean(this.citationPreview || this.activeVideoPlayback || this.loadings.citation)
      }
    },
    mounted () {
      document.documentElement.classList.add('fullscreen-page')
      this.loadPacks()
      this.checkAdminStatus()
      this._startCharExprCycle()
    },
    beforeUnmount () {
      document.documentElement.classList.remove('fullscreen-page')
      Object.values(this.videoPollingTimers).forEach(timer => clearInterval(timer))
      this._disconnectQaWs()
      clearInterval(this._charExprTimer)
      clearTimeout(this._charExprResetTimer)
    },
    methods: {
      async loadPacks () {
        this.loadings.packs = true
        try {
          const [visibleRes, qaRes] = await Promise.all([
            api.getVisibleLanguagePackList(),
            api.getLanguagePackQaPacks()
          ])
          const visiblePacks = visibleRes.data.data || []
          const qaReadyPacks = qaRes.data.data || []
          const qaReadyPackMap = qaReadyPacks.reduce((acc, pack) => {
            acc[String(pack.id)] = pack
            return acc
          }, {})
          this.packs = visiblePacks.map(pack => {
            const qaReadyPack = qaReadyPackMap[String(pack.id)]
            return {
              ...pack,
              qa_ready: Boolean(qaReadyPack),
              page_count: qaReadyPack ? qaReadyPack.page_count : pack.page_count
            }
          })
          if (!this.packs.length) {
            this.qaAvailabilityState = 'empty'
            return
          }
          const decodedCtx = decodeQaCtx(this.$route.query.ctx)
          const routePackId = decodedCtx.p
          const firstPackId = routePackId && this.packs.some(pack => String(pack.id) === String(routePackId))
            ? routePackId
            : String(this.packs[0].id)
          await this.switchPack(firstPackId)
        } catch (error) {
          const msg = error && error.response ? `${error.response.status} ${JSON.stringify(error.response.data).slice(0, 120)}` : String(error).slice(0, 120)
          notify.error('[QA] loadPacks: ' + msg)
          if (!this.packs.length) {
            this.qaAvailabilityState = 'empty'
          }
        } finally {
          this.loadings.packs = false
        }
      },
      async switchPack (packId) {
        this._disconnectQaWs()
        this._resetQaRuntimeContext()
        this.qaPendingQuestion = ''
        this.loadings.sending = false
        this.selectedLanguagePackId = String(packId)
        const selectedPack = this.resolvePackById(packId)
        this.resetQaMentionCache()
        this.activeSessionId = null
        this.qaContextUsage = { tokens_used: 0, tokens_limit: 0, model_name: '', last_updated: null }
        this.sessions = []
        this.messages = []
        this.citationPreview = null
        await this.$router.replace({ query: { ctx: encodeQaCtx(packId) } }).catch(() => {})
        if (this.selectedLanguagePackId !== String(packId)) {
          this.selectedLanguagePackId = String(packId)
        }
        if (!selectedPack || !selectedPack.qa_ready) {
          this.qaAvailabilityState = 'unready'
          return
        }
        this.qaAvailabilityState = 'ready'
        await this.loadSessions(String(packId))
      },
      resolvePackById (packId) {
        if (!packId) {
          return null
        }
        const packs = Array.isArray(this.$data.packs) ? this.$data.packs : []
        return packs.find(pack => String(pack.id) === String(packId)) || null
      },
      async loadSessions (explicitPackId) {
        const effectivePackId = explicitPackId || this.selectedLanguagePackId
        if (!effectivePackId) return
        if (this.selectedLanguagePackId !== effectivePackId) {
          this.selectedLanguagePackId = effectivePackId
        }
        this.loadings.sessions = true
        try {
          const res = await api.getLanguagePackQaSessions({ language_pack_id: effectivePackId })
          this.sessions = res.data.data || []
          const sessionCtx = decodeQaCtx(this.$route.query.ctx)
          const routeSessionId = sessionCtx.s
          const nextSession = this.sessions.find(item => String(item.id) === String(routeSessionId)) || this.sessions[0]
          if (nextSession) {
            await this.activateSession(nextSession.id)
            return
          }
          await this.startNewSession()
        } catch (error) {
          const msg = error && error.response ? `${error.response.status} ${JSON.stringify(error.response.data).slice(0, 120)}` : String(error).slice(0, 120)
          notify.error('[QA] loadSessions: ' + msg)
        } finally {
          this.loadings.sessions = false
        }
      },
      async startNewSession (explicitPackId) {
        const effectivePackId = explicitPackId || this.selectedLanguagePackId
        if (!effectivePackId) return
        try {
          const res = await api.createLanguagePackQaSession({
            language_pack_id: Number(effectivePackId)
          })
          const session = res.data.data
          this.sessions = [session, ...this.sessions]
          await this.activateSession(session.id)
        } catch (error) {
          const msg = error && error.response ? `${error.response.status} ${JSON.stringify(error.response.data).slice(0, 120)}` : String(error).slice(0, 120)
          notify.error('[QA] startNewSession: ' + msg)
        }
      },
      async activateSession (sessionId) {
        this._disconnectQaWs()
        this._resetQaRuntimeContext()
        this.qaPendingQuestion = ''
        this.loadings.sending = false
        this.activeSessionId = String(sessionId)
        await this.$router.replace({
          query: { ctx: encodeQaCtx(this.selectedLanguagePackId, sessionId) }
        }).catch(() => {})
        await this.refreshQaContextUsage()
        await this.loadMessages()
      },
      async loadMessages () {
        if (!this.activeSessionId) return
        this.loadings.messages = true
        try {
          const res = await api.getLanguagePackQaMessages(this.activeSessionId)
          this.messages = res.data.data || []
          this.resumeVideoPolling()
        } finally {
          this.loadings.messages = false
          this.$nextTick(() => this._scrollMessagesToBottom())
        }
      },
      async refreshQaContextUsage () {
        if (!this.activeSessionId) return
        try {
          const res = await api.getLanguagePackQaSessionUsage(this.activeSessionId, { silent: true })
          const usage = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : null)
          this.qaContextUsage = {
            tokens_used: Number(usage && usage.tokens_used) || 0,
            tokens_limit: Number(usage && usage.tokens_limit) || 0,
            model_name: usage && usage.model_name ? String(usage.model_name) : '',
            last_updated: usage && usage.last_updated ? usage.last_updated : null
          }
        } catch (_) {
          this.qaContextUsage = { tokens_used: 0, tokens_limit: 0, model_name: '', last_updated: null }
        }
      },
      resetQaMentionCache () {
        this._qaMentionCache = { packId: this.selectedLanguagePackId, pages: null, kcs: null, notebooks: null }
        if (this.composerHandlers && this.composerHandlers.refreshProvider) {
          this.composerHandlers.refreshProvider('qa-pages')
          this.composerHandlers.refreshProvider('qa-kcs')
          this.composerHandlers.refreshProvider('qa-notebooks')
        }
      },
      /**
       * 课件问答 @ 菜单的「课件页」候选项：按二级目录拆分，章号取 normalized 文档
       * 按 (sort_order, id) 1-based 序号；token 形如 @page:章.页，subgroup 让
       * AtMentionMenu 渲染为独立小节，避免长文档把整组撑成单行扁平列表。
       */
      async buildQaPageMentionItems () {
        if (!this.selectedLanguagePackId) return []
        const cache = this._qaMentionCache || {}
        if (cache.packId === this.selectedLanguagePackId && cache.pages) return cache.pages
        const documents = await this.loadQaDocumentsForMentions()
        const items = []
        documents.forEach((doc, idx) => {
          const pageCount = Number(doc.page_count) || 0
          if (pageCount <= 0) return
          const chapter = idx + 1
          const title = doc.original_filename || doc.title || `课件 ${chapter}`
          const subgroup = `第 ${chapter} 章 · ${title}`
          for (let documentPageNo = 1; documentPageNo <= pageCount; documentPageNo++) {
            items.push({
              key: `page:${doc.id}:${documentPageNo}`,
              token: `@page:${chapter}.${documentPageNo}`,
              label: `第 ${documentPageNo} 页`,
              desc: title,
              subgroup,
              hoverPreview: `${title} · 第 ${documentPageNo} 页`
            })
          }
        })
        this._qaMentionCache = Object.assign({}, cache, { packId: this.selectedLanguagePackId, pages: items })
        return items
      },
      async buildQaKcMentionItems () {
        if (!this.selectedLanguagePackId) return []
        const cache = this._qaMentionCache || {}
        if (cache.packId === this.selectedLanguagePackId && cache.kcs) return cache.kcs
        const res = await api.getKcGraph(this.selectedLanguagePackId)
        const payload = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : {})
        const nodes = Array.isArray(payload.nodes) ? payload.nodes : []
        const items = nodes.map(node => {
          const id = node.id == null ? '' : String(node.id)
          return {
            key: `kc:${id}`,
            token: `@kc:${id}`,
            label: node.name || `知识点 ${id}`,
            desc: node.chapter_title || '',
            hoverPreview: node.description || node.chapter_title || ''
          }
        }).filter(item => item.token !== '@kc:')
        this._qaMentionCache = Object.assign({}, cache, { packId: this.selectedLanguagePackId, kcs: items })
        return items
      },
      async buildQaNotebookMentionItems () {
        const cache = this._qaMentionCache || {}
        if (cache.notebooks) return cache.notebooks
        const res = await api.getLearnerNotebook({})
        const payload = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : {})
        const entries = Array.isArray(payload.entries) ? payload.entries : []
        const items = entries.map(entry => {
          const id = entry.id == null ? '' : String(entry.id)
          const label = entry.title || entry.problem_title || entry.error_taxonomy || `笔记 ${id}`
          const desc = entry.reflection || entry.root_cause || entry.breakthrough_insight || entry.content || ''
          return {
            key: `notebook:${id}`,
            token: `@notebook:${id}`,
            label,
            desc: String(desc).slice(0, 80),
            hoverPreview: String(desc).slice(0, 180)
          }
        }).filter(item => item.token !== '@notebook:')
        this._qaMentionCache = Object.assign({}, cache, { notebooks: items })
        return items
      },
      async loadQaDocumentsForMentions () {
        const res = await api.getLanguagePackDocuments(this.selectedLanguagePackId)
        const payload = res && res.data && res.data.data !== undefined ? res.data.data : (res ? res.data : [])
        return Array.isArray(payload) ? payload : []
      },
      /**
       * `/page` 同时支持二级目录 (`/page 1.7`) 与 legacy 全局页号 (`/page 7`)。
       * 章号 = normalized 文档按 (sort_order, id) 1-based 序号，与 @page 候选保持一致。
       */
      async jumpToQaPage (args) {
        const raw = String(args || '').trim()
        const chapterMatch = raw.match(/^(\d+)\.(\d+)$/)
        const documents = await this.loadQaDocumentsForMentions()
        let targetDoc = null
        let documentPageNo = 0
        if (chapterMatch) {
          const chapter = Number.parseInt(chapterMatch[1], 10)
          const pageInChapter = Number.parseInt(chapterMatch[2], 10)
          if (!Number.isInteger(chapter) || chapter <= 0 || !Number.isInteger(pageInChapter) || pageInChapter <= 0) {
            notify.info('用法：/page <章.页> 或 /page <全局页>')
            return
          }
          const doc = documents[chapter - 1]
          if (!doc) {
            notify.warning(`找不到第 ${chapter} 章`)
            return
          }
          const pageCount = Number(doc.page_count) || 0
          if (pageInChapter > pageCount) {
            notify.warning(`第 ${chapter} 章只有 ${pageCount} 页`)
            return
          }
          targetDoc = doc
          documentPageNo = pageInChapter
        } else {
          const pageNo = Number.parseInt(raw, 10)
          if (!Number.isInteger(pageNo) || pageNo <= 0) {
            notify.info('用法：/page <章.页> 或 /page <全局页>')
            return
          }
          let remaining = pageNo
          for (const doc of documents) {
            const pageCount = Number(doc.page_count) || 0
            if (pageCount <= 0) continue
            if (remaining <= pageCount) {
              targetDoc = doc
              documentPageNo = remaining
              break
            }
            remaining -= pageCount
          }
          if (!targetDoc) {
            notify.warning(`找不到第 ${pageNo} 页`)
            return
          }
        }
        await this.openCitation({
          document_id: targetDoc.id,
          document_title: targetDoc.original_filename || targetDoc.title || '课件',
          page_no: documentPageNo
        })
      },
      _scrollMessagesToBottom () {
        const el = this.$refs.messageList
        if (el) el.scrollTop = el.scrollHeight
      },
      resumeVideoPolling () {
        for (const message of this.messages) {
          const job = this.resolveVideoJob(message)
          if (job && job.status !== 'completed' && job.status !== 'failed' && !this.videoPollingTimers[message.id]) {
            this.startVideoPolling(job.id, message.id)
          }
        }
      },
      _startCharExprCycle () {
        const expressions = {
          nene: ['gentle_smile', 'smile', 'thinking', 'normal', 'blush'],
          yoshino: ['glasses_adjust', 'slight_smile', 'cold', 'normal'],
          ayase: ['grin', 'competitive', 'soft_smile', 'normal'],
          kanna: ['contemplative', 'absorbed', 'slight_smile', 'normal'],
          murasame: ['smirk', 'cold', 'normal', 'impressed']
        }
        this._charExprTimer = setInterval(() => {
          if (this.loadings.sending) return
          const pool = expressions[this.qaCharacterId] || expressions.nene
          const pick = pool[Math.floor(Math.random() * pool.length)]
          this.qaCharExpression = pick
          this._charExprResetTimer = setTimeout(() => {
            this.qaCharExpression = ''
          }, 2000)
        }, 6000 + Math.random() * 4000)
      },

      async askSuggested (question) {
        if (!this.activeSessionId && this.currentPackIsQaReady) {
          await this.startNewSession()
        }
        this.composerHandlers.setText(question)
        this.$nextTick(() => this.composerHandlers.submit())
      },

      async sendQuestion (textOverride) {
        const question = String(textOverride == null ? this.rawText : textOverride).trim()
        if (!question || !this.selectedLanguagePackId || !this.activeSessionId || this.loadings.sending || this.qaInputDisabled) return
        if (await checkInputSequence(question)) {
          this.composerHandlers.clear()
          this.$nextTick(() => { this.$refs.moRef && this.$refs.moRef.play() })
          return
        }
        if (this.looksLikeOjProblemQuestion(question)) {
          notify.warning(this.ojQuestionGuardMessage)
          return
        }
        const matched = matchCharacterForQuestion(question)
        if (matched) this.qaCharacterId = matched.id
        this.qaCharExpression = getExpressionForEvent(this.qaCharacterId, 'thinking')
        this.loadings.sending = true
        this.qaPendingQuestion = question

        const wsReady = await this._ensureQaWsReady()
        if (!wsReady) {
          await this._sendQuestionSync(question)
          return
        }

        try {
          const references = parseReferences(question)
          const res = await api.sendLanguagePackQaMessage(this.activeSessionId, { content: question, references }, { async: true })
          const data = res.data && res.data.data !== undefined ? res.data.data : res.data
          this.composerHandlers.clear()

          if (data && data.status === 'dispatched') {
            this.qaRuntimeContext = {
              sessionId: String(data.session_id),
              taskId: data.task_id,
              runtimeState: 'QUEUED',
              serverEvent: null,
              failureBucket: null,
              lastError: null,
              updatedAt: new Date().toISOString()
            }
            return
          }
          await this._onQaCompleted()
        } catch (error) {
          this.loadings.sending = false
          this.qaPendingQuestion = ''
          this._resetQaRuntimeContext()
          notify.error('发送失败，请稍后重试')
        }
      },

      async _sendQuestionSync (question) {
        try {
          const references = parseReferences(question)
          await api.sendLanguagePackQaMessage(this.activeSessionId, { content: question, references })
          this.composerHandlers.clear()
          await this._onQaCompleted()
        } catch (error) {
          notify.error('发送失败，请稍后重试')
        } finally {
          this.loadings.sending = false
          this.qaPendingQuestion = ''
        }
      },

      async _onQaCompleted () {
        this.loadings.sending = false
        this.qaPendingQuestion = ''
        this.qaCharExpression = getExpressionForEvent(this.qaCharacterId, 'card_delivered')
        setTimeout(() => { this.qaCharExpression = '' }, 3000)
        await this.loadMessages()
        await this.refreshQaContextUsage()
        this.$nextTick(() => this._scrollMessagesToBottom())
        const latestAssistant = [...this.messages].reverse().find(item => item.role === 'assistant')
        const firstCitation = latestAssistant && this.resolveCitations(latestAssistant)[0]
        if (firstCitation) {
          await this.openCitation(firstCitation)
        }
      },

      _handleQaRuntimeEvent (msg) {
        const normalized = normalizeRuntimeEvent(msg)
        assertAllowedForQaPage(normalized.runtimeState)

        if (normalized.sessionId && normalized.sessionId !== String(this.activeSessionId)) {
          return
        }
        if (normalized.data && normalized.data.usage) {
          this.qaContextUsage = {
            tokens_used: Number(normalized.data.usage.tokens_used) || 0,
            tokens_limit: Number(normalized.data.usage.tokens_limit) || 0,
            model_name: normalized.data.usage.model_name ? String(normalized.data.usage.model_name) : '',
            last_updated: normalized.data.usage.last_updated || null
          }
        }

        this.qaRuntimeContext = {
          sessionId: normalized.sessionId || this.qaRuntimeContext.sessionId,
          taskId: normalized.taskId || this.qaRuntimeContext.taskId,
          runtimeState: normalized.runtimeState || this.qaRuntimeContext.runtimeState,
          serverEvent: normalized.serverEvent || this.qaRuntimeContext.serverEvent,
          failureBucket: normalized.failureBucket !== undefined ? normalized.failureBucket : this.qaRuntimeContext.failureBucket,
          lastError: this.qaRuntimeContext.lastError,
          updatedAt: normalized.timestamp || new Date().toISOString()
        }

        const serverEvent = normalized.serverEvent
        switch (serverEvent) {
          case SERVER_EVENTS.TASK_STARTED:
            this.qaRuntimeContext.runtimeState = 'RUNNING'
            break

          case SERVER_EVENTS.TASK_COMPLETED:
            this._resetQaRuntimeContext()
            this._onQaCompleted()
            break

          case SERVER_EVENTS.TASK_FAILED:
            this.qaRuntimeContext.lastError = (normalized.data && normalized.data.error) || '任务执行失败'
            this.loadings.sending = false
            break

          case SERVER_EVENTS.TASK_EXPIRED:
            this.loadings.sending = false
            this._resetQaRuntimeContext()
            notify.warning('任务已超时，请重新发送')
            break

          default:
            break
        }
      },

      _resetQaRuntimeContext () {
        this.qaRuntimeContext = {
          sessionId: null,
          taskId: null,
          runtimeState: null,
          serverEvent: null,
          failureBucket: null,
          lastError: null,
          updatedAt: null
        }
      },

      _connectQaWs () {
        if (!this.activeSessionId) return
        if (this._qaWsConnection &&
          (this._qaWsConnection.readyState === WebSocket.CONNECTING || this._qaWsConnection.readyState === WebSocket.OPEN)) {
          return
        }
        this._disconnectQaWs()

        const wsUrl = buildWebSocketUrl(buildQaWebSocketPath(this.activeSessionId))
        const ws = new WebSocket(wsUrl)
        let settleReady = null
        let readySettled = false
        this._qaWsReadyPromise = new Promise((resolve, reject) => {
          settleReady = { resolve, reject }
        })

        ws.onopen = () => {
          if (this._qaWsConnection === ws && !readySettled) {
            readySettled = true
            settleReady.resolve(true)
          }
        }

        ws.onmessage = (evt) => {
          let msg
          try { msg = JSON.parse(evt.data) } catch (_) { return }
          if (msg.type === 'runtime_event') {
            this._handleQaRuntimeEvent(msg)
          }
        }

        ws.onclose = () => {
          if (this._qaWsConnection === ws) {
            if (!readySettled) {
              readySettled = true
              settleReady.reject(new Error('QA websocket closed before ready'))
            }
            this._qaWsReadyPromise = null
            this._qaWsConnection = null
            this._qaWsReconnectTimer = setTimeout(() => this._connectQaWs(), 3000)
          }
        }

        ws.onerror = () => {
          if (!readySettled) {
            readySettled = true
            settleReady.reject(new Error('QA websocket connection failed'))
          }
        }

        this._qaWsConnection = ws
      },

      async _ensureQaWsReady () {
        if (!this.activeSessionId) return false
        if (this._qaWsConnection && this._qaWsConnection.readyState === WebSocket.OPEN) {
          return true
        }
        this._connectQaWs()
        if (!this._qaWsReadyPromise) return false
        try {
          await this._qaWsReadyPromise
          return this._qaWsConnection && this._qaWsConnection.readyState === WebSocket.OPEN
        } catch (_) {
          return false
        }
      },

      retryLastQuestion () {
        if (!this.qaPendingQuestion) return
        this._resetQaRuntimeContext()
        this.composerHandlers.setText(this.qaPendingQuestion)
        this.qaPendingQuestion = ''
        this.$nextTick(() => this.composerHandlers.submit())
      },

      _disconnectQaWs () {
        if (this._qaWsReconnectTimer) {
          clearTimeout(this._qaWsReconnectTimer)
          this._qaWsReconnectTimer = null
        }
        this._qaWsReadyPromise = null
        if (this._qaWsConnection) {
          const ws = this._qaWsConnection
          this._qaWsConnection = null
          ws.onclose = null
          ws.onerror = null
          ws.onopen = null
          ws.close()
        }
      },
      async openCitation (citation) {
        if (!citation || !citation.document_id || !citation.page_no || !this.selectedLanguagePackId) return
        this.activeCitation = citation
        this.loadings.citation = true
        try {
          const res = await api.getLanguagePackQaCitationPage(this.selectedLanguagePackId, citation.document_id, citation.page_no)
          this.citationPreview = res.data.data
        } catch (error) {
          notify.error('引用页加载失败')
        } finally {
          this.loadings.citation = false
        }
      },
      async submitFeedback (messageId, label) {
        try {
          await api.submitLanguagePackQaFeedback(messageId, {
            feedback_label: label,
            comment: ''
          })
          this.feedbackByMessageId[messageId] = label
          notify.success('反馈已记录')
        } catch (error) {
          notify.error('反馈提交失败')
        }
      },
      resolveAnswerText (message) {
        if (!message || !message.answer_json) return ''
        return message.answer_json.answer_markdown || ''
      },
      renderMarkdown (text) {
        if (!text) return ''
        let html = sanitize(marked(text))
        html = html.replace(/(\d+)\^([{(]?[-\w.+]+[})]?)/g, '$1<sup>$2</sup>')
        return html
      },
      resolveCitations (message) {
        if (!message || !message.answer_json || !Array.isArray(message.answer_json.citations)) {
          return []
        }
        return message.answer_json.citations
      },
      isRefusalMessage (message) {
        return Boolean(message && message.answer_json && message.answer_json.insufficient_evidence)
      },
      isGroundedMessage (message) {
        return Boolean(message && message.answer_json && message.answer_json.grounded)
      },
      looksLikeOjProblemQuestion (question) {
        const normalized = this.normalizeQuestionForDetection(question)
        if (!normalized) {
          return false
        }
        if (ojStrongTerms.some(term => normalized.includes(term))) {
          return true
        }
        if (shortOjVerdictPattern.test(normalized)) {
          return true
        }
        return this.containsAnyTerm(normalized, ojProblemTerms) && this.containsAnyTerm(normalized, ojSolvingTerms)
      },
      containsAnyTerm (haystack, terms) {
        return terms.some(term => haystack.includes(term))
      },
      normalizeQuestionForDetection (text) {
        if (!text) {
          return ''
        }
        return text
          .trim()
          .toLowerCase()
          .replace(/[^\p{Script=Han}\p{Letter}\p{Number}]+/gu, ' ')
          .replace(/\s+/g, ' ')
          .trim()
      },
      sessionTitle (session) {
        if (session.title && session.title.trim()) return session.title.trim()
        const preview = session.last_message_preview
        if (!preview) return '新会话'
        return preview.length > 24 ? preview.slice(0, 24) + '…' : preview
      },
      handlePackSelect (event) {
        this.switchPack(event.target.value)
      },
      packOptionLabel (pack) {
        if (!pack) {
          return ''
        }
        return pack.qa_ready ? pack.name : `${pack.name}（暂不可问答）`
      },
      async reloadAll () {
        if (this.isBusy) return
        await this.loadPacks()
      },
      async checkAdminStatus () {
        try {
          const res = await api.getUserInfo()
          const profile = res.data.data
          this.isAdmin = profile && (profile.admin_type === 'Admin' || profile.admin_type === 'Teacher')
        } catch (e) {
          this.isAdmin = false
        }
      },
      resolveVideoJob (message) {
        return message.video_job || null
      },
      async generateVideo (messageId) {
        this.videoGenerating[messageId] = true
        try {
          const res = await api.createLanguagePackQaVideoJob(messageId)
          const job = res.data.data
          await this.loadMessages()
          this.startVideoPolling(job.id, messageId)
        } catch (error) {
          const msg = error.response && error.response.data && error.response.data.data
            ? error.response.data.data : '视频生成请求失败'
          notify.error(msg)
        } finally {
          this.videoGenerating[messageId] = false
        }
      },
      startVideoPolling (jobId, messageId) {
        if (this.videoPollingTimers[messageId]) {
          clearInterval(this.videoPollingTimers[messageId])
        }
        this.videoPollingTimers[messageId] = setInterval(async () => {
          try {
            const res = await api.getLanguagePackQaVideoJob(jobId)
            const job = res.data.data
            if (job.status === 'completed' || job.status === 'failed') {
              clearInterval(this.videoPollingTimers[messageId])
              delete this.videoPollingTimers[messageId]
              await this.loadMessages()
            }
          } catch (e) {
            clearInterval(this.videoPollingTimers[messageId])
            delete this.videoPollingTimers[messageId]
          }
        }, 5000)
      },
      openVideo (videoJob) {
        this.activeVideoPlayback = videoJob
        this.citationPreview = null
      },
      closeVideo () {
        this.activeVideoPlayback = null
      },
      closeEvidencePanel () {
        this.activeVideoPlayback = null
        this.citationPreview = null
        this.activeCitation = null
      },
      handleQaCompactPlaceholder () {
        this.handleCompactSession()
      },
      handleCompactSession () {
        if (!this.activeSessionId) return
        api.compactLanguagePackQaSession(this.activeSessionId)
          .then(res => {
            const data = (res && res.data) || res || {}
            if (data.compacted) {
              notify.success('上下文已压缩')
              this.loadMessages(this.activeSessionId)
              this.refreshUsage()
            } else {
              notify.info(data.message || '消息数量不足，无需压缩')
            }
          })
          .catch(err => {
            console.error('[qa] compact failed', err)
            notify.error('压缩失败，请重试')
          })
      },
      handleForkSession () {
        if (!this.activeSessionId) return
        api.forkLanguagePackQaSession(this.activeSessionId, {})
          .then(res => {
            const data = (res && res.data) || res || {}
            const newId = data.session_id
            if (newId) {
              notify.success('会话已分叉')
              this.loadSessions()
              this.activateSession(newId)
            }
          })
          .catch(err => {
            console.error('[qa] fork failed', err)
            notify.error('分叉失败，请重试')
          })
      },
      exportConversationMarkdown () {
        if (!this.messages.length) {
          notify.info('当前没有可导出的消息')
          return
        }
        const title = this.currentPack ? this.currentPack.name : '课件问答'
        const header = '# ' + title + ' 对话导出\n\n时间：' + new Date().toLocaleString() + '\n\n'
        const body = this.messages.map(message => {
          const role = message.role === 'user' ? '我' : 'AI 助手'
          const text = message.role === 'assistant' ? this.resolveAnswerText(message) : message.content
          return '## ' + role + '\n\n' + (text || '') + '\n'
        }).join('\n')
        const blob = new Blob([header + body], { type: 'text/markdown;charset=utf-8' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'language-pack-qa-' + (this.activeSessionId || 'session') + '-' + Date.now() + '.md'
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        URL.revokeObjectURL(url)
      },
      async toggleStarSession (session) {
        try {
          const res = await api.toggleLanguagePackQaSessionStarred(session.id)
          const updated = res.data.data
          const idx = this.sessions.findIndex(s => String(s.id) === String(session.id))
          if (idx !== -1) {
            this.sessions[idx] = { ...this.sessions[idx], starred: updated.starred }
            this.sessions.sort((a, b) => {
              if (a.starred !== b.starred) return a.starred ? -1 : 1
              return new Date(b.update_time) - new Date(a.update_time)
            })
          }
          notify.success(updated.starred ? '已收藏' : '已取消收藏')
        } catch (error) {
          notify.error('操作失败')
        }
      },
      async confirmDeleteSession (session) {
        try {
          await ElMessageBox.confirm('确定删除这个会话吗？删除后不可恢复。', '删除会话', {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
          })
        } catch (_) {
          return
        }
        await this.deleteSession(session)
      },
      async deleteSession (session) {
        try {
          await api.deleteLanguagePackQaSession(session.id)
          this.sessions = this.sessions.filter(s => String(s.id) !== String(session.id))
          if (String(this.activeSessionId) === String(session.id)) {
            if (this.sessions.length) {
              await this.activateSession(this.sessions[0].id)
            } else {
              this.activeSessionId = null
              this.messages = []
              await this.startNewSession()
            }
          }
          notify.success('会话已删除')
        } catch (error) {
          notify.error('删除失败')
        }
      }
    }
  }
</script>

<style scoped lang="less">
  .qa-page {
    box-sizing: border-box;
    height: calc(100vh - var(--oj-content-top-offset, 64px));
    height: calc(100dvh - var(--oj-content-top-offset, 64px));
    max-height: calc(100vh - var(--oj-content-top-offset, 64px));
    max-height: calc(100dvh - var(--oj-content-top-offset, 64px));
    overflow: hidden;
    padding: 16px 0;
    background: var(--bg-base);
    font-family: var(--font-sans);
  }

  .qa-shell {
    width: 100%;
    max-width: 1440px;
    height: 100%;
    min-height: 0;
    margin: 0 auto;
    display: grid;
    grid-template-columns: 300px minmax(0, 1fr);
    gap: 16px;
    align-items: stretch;
    transition: grid-template-columns 0.25s ease;

    &.evidence-visible {
      grid-template-columns: 300px minmax(0, 1fr) 340px;
    }
  }

  .qa-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius-md);
    box-shadow: var(--shadow-sm);
  }

  .qa-sidebar {
    min-height: 0;
    padding: 16px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .qa-sidebar-char {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    margin-bottom: 12px;
    border-radius: 10px;
    background: var(--sc-bg, rgba(244,194,208,0.12));
    border: 1px solid color-mix(in srgb, var(--sc-color, #F4C2D0) 25%, transparent);
    transition: background 0.5s, border-color 0.5s;
  }

  .qa-sidebar-char-img {
    width: 42px;
    height: 52px;
    border-radius: 8px;
    object-fit: cover;
    object-position: top center;
    flex-shrink: 0;
    filter: drop-shadow(0 1px 4px rgba(0,0,0,0.1));
  }

  .qa-sidebar-char-info {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }

  .qa-sidebar-char-name {
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
  }

  .qa-sidebar-char-role {
    font-size: 11px;
    color: var(--text-secondary, #909399);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .qa-evidence {
    min-height: 0;
    padding: 16px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .qa-main {
    min-height: 0;
    padding: 18px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .qa-pane-head,
  .qa-main-head {
    flex-shrink: 0;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .qa-pane-head-tight {
    margin-bottom: 8px;
  }

  .qa-eyebrow {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--primary-color);
    margin-bottom: 4px;
  }

  h1, h2, h3 {
    margin: 0;
    font-family: var(--font-sans);
    color: var(--text-primary);
  }

  .qa-sidebar h1 {
    font-size: 18px;
    font-weight: 700;
    line-height: 1.3;
  }

  .qa-main h2 {
    font-size: 20px;
    font-weight: 700;
    line-height: 1.3;
  }

  .qa-main-head p,
  .qa-empty-inline,
  .qa-main-empty p,
  .qa-pack-meta {
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.6;
    margin-top: 4px;
  }

  .qa-pack-warning {
    margin-top: 6px;
    color: var(--danger-color);
    font-size: 12px;
    line-height: 1.5;
    font-weight: 600;
  }

  .qa-section-label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    letter-spacing: 0.04em;
    margin-bottom: 6px;
  }

  .qa-pack-section {
    flex-shrink: 0;
    margin-top: 14px;
    padding: 12px;
    background: var(--bg-panel);
    border-radius: var(--border-radius-sm);
    border: 1px solid var(--border-color);
  }

  .qa-pack-select {
    width: 100%;
    min-height: 36px;
    border-radius: var(--border-radius-sm);
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    padding: 0 10px;
    font-size: 14px;
    font-family: var(--font-sans);
    color: var(--text-primary);
    outline: none;
    transition: border-color 0.2s;

    &:focus {
      border-color: var(--primary-color);
    }
  }

  .qa-pack-select:disabled {
    cursor: not-allowed;
    opacity: 0.6;
    background: var(--bg-panel);
  }

  .qa-session-head {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 16px 0 8px;
  }

  .qa-session-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .qa-session-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    object-position: top center;
    flex-shrink: 0;
    border: 1.5px solid var(--border-color, #e4e7ed);
  }

  .qa-session-item.is-active .qa-session-avatar {
    border-color: var(--primary-color);
  }

  .qa-session-item {
    width: 100%;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    border-radius: var(--border-radius-sm);
    padding: 10px 12px;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .qa-session-item:hover,
  .qa-session-item:focus-visible {
    border-color: var(--primary-color);
    box-shadow: var(--shadow-sm);
    outline: none;
  }

  .qa-session-item.is-active {
    border-color: var(--primary-color);
    background: rgba(37, 99, 235, 0.04);
  }

  .qa-session-item.is-starred {
    border-left: 3px solid #f59e0b;
  }

  .qa-session-title {
    flex: 1;
    min-width: 0;
    font-weight: 500;
    font-size: 13px;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    line-height: 1.5;
  }

  .qa-session-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 2px;
    opacity: 0.45;
    transition: opacity 0.15s;
  }

  .qa-session-item:hover .qa-session-actions,
  .qa-session-item.is-active .qa-session-actions {
    opacity: 1;
  }

  .qa-session-item .qa-session-actions:has(.is-starred) {
    opacity: 1;
  }

  .qa-session-action-btn {
    width: 26px;
    height: 26px;
    border: none;
    background: transparent;
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: var(--text-secondary);
    font-size: 15px;
    padding: 0;
    transition: color 0.15s, background 0.15s;
  }

  .qa-session-action-btn:hover {
    background: var(--bg-panel);
    color: var(--text-primary);
  }

  .qa-session-action-btn.is-starred {
    color: #f59e0b;
  }

  .qa-session-action-btn.is-starred:hover {
    color: #d97706;
    background: rgba(245, 158, 11, 0.08);
  }

  .qa-session-action-btn.is-danger {
    color: #9ca3af;
  }

  .qa-session-action-btn.is-danger:hover {
    color: #ef4444;
    background: rgba(239, 68, 68, 0.06);
  }

  .qa-status-pill {
    flex-shrink: 0;
    height: 32px;
    padding: 0 12px;
    border-radius: 999px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    font-size: 12px;
    border: 1px solid transparent;
    white-space: nowrap;
  }

  .qa-status-pill.is-ready {
    background: rgba(16, 185, 129, 0.08);
    color: #047857;
    border-color: rgba(16, 185, 129, 0.2);
  }

  .qa-status-pill.is-empty,
  .qa-answer-state.is-refusal {
    background: rgba(239, 68, 68, 0.06);
    color: #b91c1c;
    border-color: rgba(239, 68, 68, 0.15);
  }

  .qa-status-pill.is-unready {
    background: rgba(245, 158, 11, 0.08);
    color: #b45309;
    border-color: rgba(245, 158, 11, 0.2);
  }

  .qa-status-pill.is-loading {
    background: rgba(37, 99, 235, 0.06);
    color: var(--primary-color);
    border-color: rgba(37, 99, 235, 0.15);
  }

  .qa-message-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 14px 6px 8px 0;
  }

  .qa-message {
    display: grid;
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 10px;
  }

  .qa-main-head-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .qa-head-char {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    overflow: hidden;
    border: 2px solid #F4C2D0;
    flex-shrink: 0;
    background: rgba(244,194,208,0.08);
  }

  .qa-head-char-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: top center;
    display: block;
  }

  .qa-eyebrow-sep {
    margin: 0 4px;
    opacity: 0.4;
  }

  .qa-welcome-scene {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex: 1;
    padding: 24px 20px;
    gap: 24px;
  }

  .qa-welcome-char-area {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }

  .qa-welcome-sprite {
    width: 120px;
    height: 160px;
    object-fit: cover;
    object-position: top center;
    border-radius: 16px;
    filter: drop-shadow(0 4px 12px rgba(0,0,0,0.1));
    animation: qa-char-float 4s ease-in-out infinite;
    transition: opacity 0.4s ease;
  }

  @keyframes qa-char-float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-4px); }
  }

  .qa-welcome-bubble {
    text-align: center;
    max-width: 320px;
    padding: 12px 18px;
    border-radius: 14px;
    background: var(--bg-panel, #f5f7fa);
    border: 1px solid var(--border-color, #e4e7ed);
    position: relative;
  }

  .qa-welcome-name {
    font-size: 14px;
    font-weight: 700;
    display: block;
    margin-bottom: 4px;
  }

  .qa-welcome-text {
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-secondary, #606266);
    margin: 0;
  }

  .qa-suggest-section {
    width: 100%;
    max-width: 480px;
  }

  .qa-suggest-label {
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary, #909399);
    margin-bottom: 10px;
    text-align: center;
  }

  .qa-suggest-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: center;
  }

  .qa-suggest-chip {
    padding: 8px 16px;
    border-radius: 20px;
    border: 1px solid var(--border-color, #e4e7ed);
    background: var(--bg-card, #fff);
    color: var(--text-primary, #303133);
    font-size: 13px;
    cursor: pointer;
    transition: background 0.2s, border-color 0.2s, transform 0.15s;

    &:hover {
      background: var(--bg-panel, #f5f7fa);
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(0);
    }
  }

  .qa-char-role-tag {
    font-size: 11px;
    color: var(--text-secondary);
    font-weight: 400;
    margin-left: 6px;
  }

  .qa-avatar-char {
    border: 2px solid #F4C2D0;
    padding: 0;
    overflow: hidden;
    background: rgba(244,194,208,0.1);
  }

  .qa-avatar-sprite {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: top center;
    display: block;
  }

  .qa-avatar {
    width: 36px;
    height: 36px;
    border-radius: var(--border-radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 700;
    color: #fff;
    background: var(--primary-color);
    flex-shrink: 0;
  }

  .qa-message.is-user .qa-avatar {
    background: var(--secondary-color);
  }

  .qa-user-avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: inherit;
    display: block;
  }

  .qa-bubble {
    border-radius: var(--border-radius-md);
    padding: 14px 16px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
  }

  .qa-message.is-assistant .qa-bubble {
    background: var(--bg-panel);
    border-color: var(--border-color);
  }

  .qa-message-role {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-secondary);
    letter-spacing: 0.06em;
    text-transform: uppercase;
    margin-bottom: 8px;
  }

  .qa-answer-text,
  .qa-question-text,
  .qa-evidence-text,
  .qa-evidence-excerpt {
    color: var(--text-primary);
    font-size: 14px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .qa-answer-markdown {
    white-space: normal;

    :deep(p) {
      margin: 0 0 8px;
      &:last-child { margin-bottom: 0; }
    }
    :deep(strong) { font-weight: 700; }
    :deep(em) { font-style: italic; }
    :deep(ul), :deep(ol) {
      margin: 4px 0 8px;
      padding-left: 20px;
    }
    :deep(li) { margin: 2px 0; }
    :deep(code) {
      background: var(--bg-panel);
      border: 1px solid var(--border-color);
      border-radius: 3px;
      padding: 1px 5px;
      font-family: var(--font-mono);
      font-size: 13px;
    }
    :deep(table) {
      border-collapse: collapse;
      width: 100%;
      margin: 8px 0;
      font-size: 13px;
    }
    :deep(th), :deep(td) {
      border: 1px solid var(--border-color);
      padding: 8px 12px;
      text-align: left;
    }
    :deep(th) {
      background: var(--bg-panel);
      font-weight: 600;
      color: var(--text-primary);
    }
    :deep(tr:nth-child(even)) {
      background: rgba(0, 0, 0, 0.02);
    }
    :deep(pre) {
      background: var(--bg-panel);
      border: 1px solid var(--border-color);
      border-radius: var(--border-radius-sm);
      padding: 10px 12px;
      overflow-x: auto;
      margin: 6px 0;
      code {
        background: none;
        border: none;
        padding: 0;
        font-size: 13px;
      }
    }
    :deep(blockquote) {
      border-left: 3px solid var(--primary-color);
      margin: 6px 0;
      padding: 4px 10px;
      color: var(--text-secondary);
    }
    :deep(h1), :deep(h2), :deep(h3), :deep(h4) {
      margin: 10px 0 4px;
      font-weight: 700;
    }
  }

  .qa-answer-state {
    display: inline-flex;
    align-items: center;
    height: 28px;
    padding: 0 10px;
    border-radius: 999px;
    margin-top: 10px;
    background: rgba(37, 99, 235, 0.06);
    color: var(--primary-color);
    font-size: 12px;
    font-weight: 600;
  }

  .qa-citation-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
  }

  .qa-citation-chip,
  .qa-feedback-btn,
  .qa-ghost-btn,
  .qa-primary-btn {
    height: 34px;
    border-radius: var(--border-radius-sm);
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    padding: 0 12px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 500;
    font-family: var(--font-sans);
    cursor: pointer;
    transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
  }

  .qa-citation-chip:hover,
  .qa-feedback-btn:hover,
  .qa-ghost-btn:hover,
  .qa-primary-btn:hover,
  .qa-citation-chip:focus-visible,
  .qa-feedback-btn:focus-visible,
  .qa-ghost-btn:focus-visible,
  .qa-primary-btn:focus-visible {
    box-shadow: var(--shadow-sm);
    outline: none;
  }

  .qa-citation-chip {
    color: var(--primary-color);
    background: rgba(37, 99, 235, 0.04);
    border-color: rgba(37, 99, 235, 0.15);

    &:hover {
      background: rgba(37, 99, 235, 0.08);
      border-color: var(--primary-color);
    }
  }

  .qa-feedback-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
  }

  .qa-feedback-btn {
    color: var(--text-secondary);

    &:hover {
      color: var(--text-primary);
      border-color: var(--primary-color);
    }
  }

  .qa-feedback-btn.is-selected {
    color: var(--primary-color);
    border-color: var(--primary-color);
    background: rgba(37, 99, 235, 0.04);
  }

  .qa-primary-btn {
    background: var(--primary-color);
    color: #fff;
    border-color: transparent;
    font-weight: 600;

    &:hover {
      background: var(--primary-hover);
    }
  }

  .qa-primary-btn-small {
    height: 30px;
    padding: 0 10px;
    font-size: 12px;
  }

  .qa-ghost-btn {
    color: var(--text-primary);
    background: var(--bg-card);

    &:hover {
      border-color: var(--primary-color);
      color: var(--primary-color);
    }
  }

  .qa-primary-btn:disabled,
  .qa-ghost-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
  }

  .qa-main-empty {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    padding: 24px;
  }

  .qa-main-empty-compact {
    min-height: 0;
  }

  .qa-empty-icon {
    font-size: 40px;
    color: var(--text-disabled);
    margin-bottom: 12px;
  }

  .qa-main-empty h3 {
    font-size: 16px;
    margin-bottom: 6px;
  }

  .qa-composer {
    position: relative;
    flex-shrink: 0;
    margin-top: 12px;
    border-radius: var(--border-radius-md);
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    padding: 12px;
  }

  .qa-guard-rail {
    margin-bottom: 10px;
    padding: 8px 12px;
    border-radius: var(--border-radius-sm);
    border: 1px solid rgba(245, 158, 11, 0.2);
    background: rgba(245, 158, 11, 0.04);
    color: #b45309;
    font-size: 12px;
    line-height: 1.5;
    font-weight: 500;
  }

  .qa-composer-actions {
    margin-top: 10px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .qa-composer-hint {
    font-size: 12px;
    color: var(--text-disabled);
    font-family: var(--font-mono);
  }

  .qa-evidence-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .qa-evidence-card {
    flex-shrink: 0;
    padding: 14px;
    border-radius: var(--border-radius-sm);
    background: var(--bg-panel);
    border: 1px solid var(--border-color);
  }

  .qa-evidence-title {
    font-weight: 700;
    font-size: 14px;
    color: var(--text-primary);
    margin-bottom: 2px;
  }

  .qa-evidence-meta {
    color: var(--text-secondary);
    font-size: 12px;
    margin-bottom: 10px;
  }

  .qa-preview-frame-wrap {
    flex-shrink: 0;
    border-radius: var(--border-radius-sm);
    overflow: hidden;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    display: flex;
    flex-direction: column;
  }

  .qa-preview-frame-wrap :deep(.pdf-page-viewer) {
    min-height: 320px;
  }

  .qa-preview-open-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    width: 100%;
    padding: 10px 16px;
    font-size: 13px;
    font-weight: 500;
    color: var(--primary-color);
    text-decoration: none;
    border-top: 1px solid var(--border-color);
    transition: background 0.15s;

    &:hover {
      background: rgba(37, 99, 235, 0.05);
      text-decoration: underline;
    }
  }

  .qa-evidence-context {
    padding: 10px 0 0;
  }

  .qa-empty-inline-warning {
    color: #b45309;
    font-weight: 500;
  }

  .qa-session-skeleton,
  .qa-message-skeleton {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .qa-skeleton-line,
  .qa-bubble-skeleton,
  .qa-big-skeleton {
    border-radius: var(--border-radius-sm);
    background: linear-gradient(90deg, var(--bg-panel), var(--bg-card), var(--bg-panel));
    background-size: 200% 100%;
    animation: qa-skeleton 1.4s ease infinite;
    user-select: none;
    -webkit-user-select: none;
    pointer-events: none;
  }

  .qa-skeleton-line {
    height: 52px;
  }

  .qa-bubble-skeleton {
    height: 80px;
  }

  .qa-big-skeleton {
    width: 100%;
    height: 120px;
  }

  .qa-big-skeleton.short {
    height: 18px;
    margin-top: 10px;
  }

  @keyframes qa-skeleton {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }

  @media (max-width: 1180px) {
    .qa-shell {
      grid-template-columns: 220px minmax(0, 1fr);

      &.evidence-visible {
        grid-template-columns: 220px minmax(0, 1fr) 280px;
      }
    }
  }

  @media (max-width: 900px) {
    .qa-shell {
      grid-template-columns: 180px minmax(0, 1fr);

      &.evidence-visible {
        grid-template-columns: 180px minmax(0, 1fr) 220px;
      }
    }
  }

  @media (max-width: 768px) {
    .qa-page {
      padding: 8px 0;
      overflow-x: auto;
    }

    .qa-shell {
      gap: 10px;
      min-width: 640px;
      grid-template-columns: 160px minmax(0, 1fr);

      &.evidence-visible {
        grid-template-columns: 160px minmax(0, 1fr) 200px;
      }
    }

    .qa-main,
    .qa-sidebar,
    .qa-evidence {
      padding: 12px;
    }

    .qa-composer-actions {
      flex-direction: column;
      align-items: stretch;
    }
  }

  .qa-video-row {
    margin-top: 8px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .qa-video-status {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
  }

  .qa-video-status.is-completed .qa-video-link {
    color: var(--qa-accent, #6366f1);
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
    font-weight: 500;
  }

  .qa-video-link:hover {
    text-decoration: underline;
  }

  .qa-video-status.is-failed .qa-video-failed {
    color: #ef4444;
  }

  .qa-video-progress {
    color: #a1a1aa;
    display: flex;
    align-items: center;
    gap: 4px;
    user-select: none;
    -webkit-user-select: none;
  }

  .qa-video-player-wrap {
    padding: 16px;
  }

  .qa-video-player {
    width: 100%;
    border-radius: 8px;
    background: #000;
    max-height: 420px;
  }

  .qa-video-meta {
    margin-top: 8px;
    font-size: 12px;
    color: #a1a1aa;
    text-align: center;
  }

  .qa-runtime-status {
    flex-shrink: 0;
    margin-top: 12px;
  }

  .qa-runtime-banner {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 14px;
    border-radius: var(--border-radius-sm);
    font-size: 13px;
    font-weight: 500;
  }

  .qa-runtime-banner.is-queued,
  .qa-runtime-banner.is-running {
    background: rgba(37, 99, 235, 0.06);
    border: 1px solid rgba(37, 99, 235, 0.15);
    color: var(--primary-color);
  }

  .qa-runtime-banner.is-failed {
    background: rgba(239, 68, 68, 0.05);
    border: 1px solid rgba(239, 68, 68, 0.18);
    color: #B91C1C;
  }

  .qa-runtime-spin {
    animation: qa-runtime-spin 1s linear infinite;
    flex-shrink: 0;
    margin-top: 2px;
  }

  @keyframes qa-runtime-spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  .qa-runtime-failed-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .qa-failure-bucket {
    font-size: 11px;
    font-family: var(--font-mono);
    color: #991B1B;
    opacity: 0.7;
  }

  .qa-retry-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 28px;
    padding: 0 10px;
    border-radius: var(--border-radius-sm);
    border: 1px solid rgba(37, 99, 235, 0.2);
    background: var(--bg-card);
    color: var(--primary-color);
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.15s;
    width: fit-content;
  }

  .qa-retry-btn:hover {
    background: rgba(37, 99, 235, 0.06);
    border-color: var(--primary-color);
  }

  @media (prefers-reduced-motion: reduce) {
    .qa-skeleton-line,
    .qa-bubble-skeleton,
    .qa-big-skeleton,
    .qa-session-item,
    .qa-citation-chip,
    .qa-feedback-btn,
    .qa-ghost-btn,
    .qa-primary-btn,
    .qa-runtime-spin {
      animation: none;
      transition: none;
    }
  }
</style>
