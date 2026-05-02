<template>
  <div class="collaborative-coding">
    <div class="collab-floatbar" v-if="floatbarVisible">
      <div class="cf-left">
        <div class="cf-icon">
          <el-icon><Notebook /></el-icon>
        </div>
        <div class="cf-title" :title="floatbarTitle">{{ floatbarTitle }}</div>
        <el-tag class="cf-mode" :type="getModeColor(modeKey)" :style="getModeTagStyle(modeKey)">{{ getModeText(modeKey) }}</el-tag>
      </div>
      <div class="cf-right">
        <div class="cf-chip cf-token" v-if="modeKey === 'RELAY'">
          <el-icon><Key /></el-icon>
          <span class="cf-chip-label">令牌</span>
          <span class="cf-chip-val">{{ tokenHolderDisplay }}</span>
        </div>
        <div class="cf-chip cf-timer" v-if="modeKey === 'RELAY' && relayRemainingSeconds !== null">
          <el-icon><Timer /></el-icon>
          <span class="cf-chip-label">剩余</span>
          <span class="cf-chip-val mono">{{ formatRemaining(relayRemainingSeconds) }}</span>
        </div>

        <div class="cf-chip cf-queue" v-if="modeKey === 'RELAY' && myQueuePosition">
          <el-icon><User /></el-icon>
          <span class="cf-chip-label">排队</span>
          <span class="cf-chip-val">#{{ myQueuePosition }}</span>
          <el-button size="small" type="text" class="cf-cancel" :loading="relay.cancelling" @click="cancelQueue">
            取消
          </el-button>
        </div>

        <el-button
          v-if="modeKey === 'RELAY' && !canEdit && !myQueuePosition"
          size="small"
          type="primary"
          :loading="relay.requesting"
          @click="requestToken"
          class="cf-action">
          申请令牌
        </el-button>
        <el-button
          v-if="modeKey === 'RELAY' && canEdit"
          size="small"
          type="warning"
          plain
          @click="releaseToken"
          class="cf-action">
          释放令牌
        </el-button>
      </div>
    </div>

    <div class="coding-header">
      <el-card>
        <el-row :gutter="16">
          <el-col :span="12">
            <h3>实时协作编程</h3>
            <p class="meta">
              <el-tag :type="getModeColor(modeKey)" :style="getModeTagStyle(modeKey)">{{ getModeText(modeKey) }}</el-tag>
              <span class="divider">|</span>
              <span>在线: {{ onlineUsers.length }} 人</span>
              <span class="divider">|</span>
              <span class="perm">
                <el-icon><Edit /></el-icon>
                输入权限:
                <strong>{{ inputPermissionText }}</strong>
              </span>
            </p>
          </el-col>
          <el-col :span="12" style="text-align: right">
            <el-button v-if="isStaff && modeKey === 'RELAY'"
                    type="primary" 
                    @click="showTransferModal = true">
              <el-icon><Refresh /></el-icon>
              转移令牌
            </el-button>
            <el-button v-if="isStaff" 
                    type="warning" 
                    @click="endSession">
              结束会话
            </el-button>
          </el-col>
        </el-row>
      </el-card>
    </div>

    <el-row :gutter="16" class="coding-content">
      <!-- 左侧：代码编辑器 -->
      <el-col :span="18" class="coding-main-col">
        <el-card class="editor-card">
          <template #header><p>
            <el-icon><Monitor /></el-icon>
            代码编辑器
            <span class="editor-title-right">
              <el-tag v-if="modeKey === 'RELAY'" :type="canEdit ? 'success' : 'info'" style="margin-left: 10px">
                <el-icon><component :is="canEdit ? 'Unlock' : 'Lock'" /></el-icon>
                {{ canEdit ? '可输入' : '只读' }}
              </el-tag>
              <el-tag v-if="modeKey === 'RELAY' && currentTokenHolder" type="success" style="margin-left: 8px">
                <el-icon><User /></el-icon>
                {{ currentTokenHolder.username }} 持有令牌
              </el-tag>
              <el-tag v-if="modeKey === 'RELAY' && relay.remainingSeconds !== null" type="warning" style="margin-left: 8px">
                <el-icon><Timer /></el-icon>
                {{ formatRemaining(relay.remainingSeconds) }}
              </el-tag>
            </span>
          </p></template>
          
          <div class="editor-container">
            <CodeMirror
              ref="cmEditor"
              class="collab-editor"
              :initial-value="code"
              :languages="['Python', 'C++', 'Java', 'JavaScript']"
              :language="language"
              theme="solarized"
              :hide-header="true"
              @change="onCodeInput"
            />
          </div>
          
          <div class="editor-toolbar">
            <el-select v-model="language" style="width: 150px" @change="changeLanguage">
              <el-option value="python" label="Python" />
              <el-option value="cpp" label="C++" />
              <el-option value="java" label="Java" />
              <el-option value="javascript" label="JavaScript" />
            </el-select>
            
            <el-button type="primary" 
                    :disabled="!canEdit" 
                    @click="runCode"
                    style="margin-left: 10px">
              <el-icon><VideoPlay /></el-icon>
              运行
            </el-button>
            
            <el-button @click="resetCode" style="margin-left: 10px">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>

            <el-button v-if="modeKey === 'RELAY' && !canEdit"
                    :loading="relay.requesting"
                    @click="requestToken"
                    style="margin-left: auto">
              <el-icon><Key /></el-icon>
              申请输入令牌
            </el-button>
            <el-button v-if="modeKey === 'RELAY' && !canEdit && myQueuePosition"
                    type="text"
                    :loading="relay.cancelling"
                    @click="cancelQueue"
                    style="margin-left: 8px">
              取消排队
            </el-button>
            <el-button v-if="modeKey === 'RELAY' && canEdit"
                    type="warning"
                    plain
                    @click="releaseToken"
                    style="margin-left: auto">
              <el-icon><SwitchButton /></el-icon>
              释放令牌
            </el-button>
          </div>

          <div v-if="modeKey === 'RELAY' && !canEdit && myQueuePosition" class="relay-queue-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>你已自动加入等待队列：第 <strong>#{{ myQueuePosition }}</strong> 位。</span>
            <span v-if="relay.tokenHolderId">当前持有者：<strong>{{ tokenHolderDisplay }}</strong></span>
          </div>

          <div v-if="modeKey === 'RELAY' && relay.waitingQueue.length" class="relay-queue">
            <div class="rq-title">
              <el-icon><User /></el-icon>
              等待队列 ({{ relay.waitingQueue.length }})
            </div>
            <div class="rq-list">
              <el-tag v-for="(item, idx) in relay.waitingQueue"
                   :key="String(item.user_id) + '_' + idx"
                   size="default"
                   class="rq-tag">
                #{{ idx + 1 }} {{ item.user_name || ('用户' + item.user_id) }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：在线用户和聊天 -->
      <el-col :span="6" class="coding-side-col">
        <el-card class="side-panel problem-panel">
          <template #header><p class="panel-title">
            <el-icon><Notebook /></el-icon>
            题目缩略
            <span class="panel-actions">
              <el-button size="small" type="text" @click="problemCollapsed = !problemCollapsed">
                <el-icon><component :is="problemCollapsed ? 'ArrowDown' : 'ArrowUp'" /></el-icon>
                {{ problemCollapsed ? '展开' : '收起' }}
              </el-button>
            </span>
          </p></template>

          <div v-if="problemLoading" class="panel-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span style="margin-left: 8px">加载题目信息...</span>
          </div>

          <div v-else-if="!problemSummary" class="panel-empty">
            <el-alert type="warning" show-icon :closable="false">
              <template #title>此协作会话未绑定题目，或题目不可访问。</template>
              <div v-if="modeKey === 'SCAFFOLDING'" class="panel-empty-hint">您仍可直接在左侧编辑器中输入代码。</div>
            </el-alert>
          </div>

          <div v-else-if="problemCollapsed" class="problem-collapsed">
            <div class="pc-title">{{ problemSummary.title }}</div>
            <el-button size="small" type="text" @click="showProblemModal = true">查看</el-button>
          </div>

          <div v-else class="problem-thumb">
            <div class="pt-head">
              <div class="pt-title">{{ problemSummary.title }}</div>
              <div class="pt-meta">
                <el-tag size="default">{{ problemSummary._id || ('ID ' + problemSummary.problemId) }}</el-tag>
                <el-tag v-if="problemSummary.difficulty" size="default">{{ problemSummary.difficulty }}</el-tag>
              </div>
            </div>
            <div class="pt-body markdown-body" v-html="sanitize(problemSummary.descriptionPreview)"></div>
            <div class="pt-footer">
              <el-button size="small" @click="showProblemModal = true">
                <el-icon><FullScreen /></el-icon>
                查看完整题面
              </el-button>
              <el-button size="small" type="text" @click="openProblemPage" :disabled="!problemSummary.problemId">
                打开题目页
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </el-card>

        <el-card class="side-panel">
          <template #header><p>
            <el-icon><User /></el-icon>
            在线用户 ({{ onlineUsers.length }})
          </p></template>
          
          <div class="user-list">
            <div v-for="user in onlineUsers" :key="user.id" class="user-item">
              <el-avatar :src="user.avatar" size="small"/>
              <span class="username">{{ user.username }}</span>
              <el-tag v-if="modeKey === 'RELAY' && Number(user.id) === Number(relay.tokenHolderId)" type="success" size="default">输入</el-tag>
              <el-icon v-if="user.is_editing" color="#19be6b"><Edit /></el-icon>
            </div>
          </div>
        </el-card>

        <el-card class="side-panel chat-panel">
          <template #header><p>
            <el-icon><ChatDotRound /></el-icon>
            聊天
          </p></template>
          
          <div class="chat-messages" ref="chatMessages">
            <div v-for="msg in messages" :key="msg.id" class="message">
              <p class="message-user">{{ msg.username }}:</p>
              <p class="message-text">{{ msg.text }}</p>
            </div>
          </div>
          
          <div class="chat-input">
            <el-input v-model="chatMessage" 
                   placeholder="输入消息..." 
                   @keyup.enter="sendMessage"/>
            <el-button type="primary" @click="sendMessage" style="margin-left: 5px">
              发送
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 运行结果 Modal -->
    <el-dialog v-model="showResultModal" title="运行结果" width="800">
      <div class="run-result">
        <el-tabs model-value="output">
          <el-tab-pane label="输出" name="output">
            <pre class="result-content">{{ runResult.output || '无输出' }}</pre>
          </el-tab-pane>
          <el-tab-pane label="错误" name="error" v-if="runResult.error">
            <pre class="result-content error">{{ runResult.error }}</pre>
          </el-tab-pane>
          <el-tab-pane label="详情" name="details">
            <p>执行时间: {{ runResult.time }}ms</p>
            <p>内存使用: {{ runResult.memory }}KB</p>
            <p>状态: {{ runResult.status }}</p>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 转移令牌 Modal -->
    <el-dialog v-model="showTransferModal" title="转移编辑令牌" width="400">
      <el-select v-model="transferTarget" placeholder="选择目标用户" style="width: 100%">
        <el-option v-for="user in onlineUsers.filter(u => Number(u.id) !== Number(relay.tokenHolderId))" 
                :key="user.id" 
                :value="user.id"
                :label="user.username" />
      </el-select>
      
      <template #footer><div>
        <el-button @click="showTransferModal = false">取消</el-button>
        <el-button type="primary" @click="transferToken">确定</el-button>
      </div></template>
    </el-dialog>

    <!-- 题目详情 Modal -->
    <el-dialog v-model="showProblemModal" :title="problemSummary ? problemSummary.title : '题目'" width="900">
      <div v-if="problemSummary" class="problem-modal-body">
        <div class="markdown-body" v-html="sanitize(problemSummary.fullHtml)"></div>
      </div>
      <template #footer><div>
        <el-button @click="showProblemModal = false">关闭</el-button>
        <el-button type="primary" @click="openProblemPage" :disabled="!problemSummary || !problemSummary.problemId">
          打开题目页
        </el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import { sanitize } from '@/utils/sanitize'
import { buildClassroomCollabWebSocketPath, buildWebSocketUrl } from '@/utils/websocketUrl'
import { decodeRouteCtx } from '@/utils/urlCipher'
import CodeMirror from '@oj/components/CodeMirror.vue'
import { ElMessageBox } from 'element-plus'
import {
  Notebook, Key, Timer, User, Edit, Refresh, Monitor, VideoPlay,
  SwitchButton, InfoFilled, Lock, Unlock, ChatDotRound, FullScreen,
  ArrowRight, ArrowDown, ArrowUp, Loading
} from '@element-plus/icons-vue'

export default {
  name: 'CollaborativeCoding',
  components: {
    CodeMirror, Notebook, Key, Timer, User, Edit, Refresh, Monitor, VideoPlay,
    SwitchButton, InfoFilled, Lock, Unlock, ChatDotRound, FullScreen,
    ArrowRight, ArrowDown, ArrowUp, Loading
  },
  props: {
    isStaff: { type: Boolean, default: false }
  },
  data () {
    return {
      session: {},
      onlineUsers: [],
      messages: [],

      code: '',
      language: 'python',
      canEdit: true,
      currentTokenHolder: null,
      suppressLocalChange: false,
      codeSyncTimer: null,

      ws: null,
      wsConnected: false,
      reconnectTimer: null,
      reconnectAttempts: 0,
      isUnmounting: false,

      chatMessage: '',

      showResultModal: false,
      runResult: {},

      showTransferModal: false,
      transferTarget: null,

      relay: {
        tokenHolderId: null,
        tokenHolderName: '',
        remainingSeconds: null,
        lastStatusTs: 0,
        waitingQueue: [],
        queuePosition: null,
        requesting: false,
        cancelling: false
      },

      problemLoading: false,
      problemCollapsed: true,
      showProblemModal: false,
      problemSummary: null,

      nowTs: Date.now(),
      ticker: null
    }
  },
  mounted () {
    this.loadSession()
    this.connectWebSocket()
    this.startTicker()
    this.$nextTick(() => {
      this.changeLanguage()
      this.refreshEditPermission()
    })
  },
  beforeUnmount () {
    this.isUnmounting = true
    if (this.ws) {
      this.ws.close()
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
    }
    if (this.codeSyncTimer) {
      clearTimeout(this.codeSyncTimer)
      this.codeSyncTimer = null
    }
    if (this.ticker) {
      clearInterval(this.ticker)
      this.ticker = null
    }
  },
  methods: {
    sanitize,
    setEditorDocument (nextCode, config = {}) {
      const normalizedCode = typeof nextCode === 'string' ? nextCode : ''
      this.code = normalizedCode
      this.$nextTick(() => {
        const editorRef = this.$refs.cmEditor
        if (editorRef && typeof editorRef.setDocument === 'function') {
          editorRef.setDocument(normalizedCode, config)
        }
      })
    },
    loadSession () {
      api.getCollaborationSession(this.classroomId, this.sessionId).then(res => {
        this.session = res.data.data
        this.language = this.session.language || 'python'
        this.loadProblemSummary()
        this.refreshEditPermission()
      })
    },

    loadProblemSummary () {
      const classroomProblemId = this.session && this.session.classroom_problem
      if (!classroomProblemId) {
        this.problemSummary = null
        return
      }
      this.problemLoading = true
      api.getClassroomProblem(this.classroomId, classroomProblemId).then(res => {
        const cp = res.data && res.data.data ? res.data.data : null
        const problemId = cp && cp.problem_id
        if (!problemId) {
          this.problemSummary = null
          this.problemLoading = false
          return
        }
        return api.getProblem(problemId).then(res2 => {
          const p = res2.data && res2.data.data ? res2.data.data : null
          if (!p) {
            this.problemSummary = null
            this.problemLoading = false
            return
          }
          const raw = (p.description || '')
          const text = String(raw)
            .replace(/<[^>]+>/g, ' ')
            .replace(/\s+/g, ' ')
            .trim()
          const clipped = text.length > 420 ? (text.slice(0, 420) + '…') : text
          const preview = `<p>${this.escapeHtml(clipped || '（空）')}</p>`
          const fullHtml = [
            `<p class="title">题目描述</p><div class="content">${p.description || ''}</div>`,
            `<p class="title">输入</p><div class="content">${p.input_description || ''}</div>`,
            `<p class="title">输出</p><div class="content">${p.output_description || ''}</div>`
          ].join('')
          this.problemSummary = {
            classroomProblemId,
            problemId: p.id,
            _id: p._id,
            title: p.title || '',
            difficulty: p.difficulty || '',
            descriptionPreview: preview,
            fullHtml
          }
          this.problemLoading = false
        })
      }).catch(() => {
        this.problemSummary = null
        this.problemLoading = false
      })
    },

    escapeHtml (s) {
      return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
    },

    onCodeInput (newCode) {
      this.code = newCode
      if (this.suppressLocalChange) return
      if (!this.canEdit) return
      if (!this.ws || this.ws.readyState !== window.WebSocket.OPEN) return
      if (this.codeSyncTimer) clearTimeout(this.codeSyncTimer)
      this.codeSyncTimer = setTimeout(() => {
        if (!this.ws || this.ws.readyState !== window.WebSocket.OPEN) return
        this.ws.send(JSON.stringify({
          type: 'code_update',
          user_id: this.$store.getters.user.id,
          code: this.code
        }))
      }, 120)
    },

    connectWebSocket () {
      if (!this.sessionId) return
      if (this.ws && (this.ws.readyState === window.WebSocket.OPEN || this.ws.readyState === window.WebSocket.CONNECTING)) {
        return
      }
      const wsUrl = buildWebSocketUrl(buildClassroomCollabWebSocketPath(this.sessionId))
      this.ws = new window.WebSocket(wsUrl)

      this.ws.onopen = () => {
        // WebSocket connected
        this.wsConnected = true
        this.reconnectAttempts = 0
      }

      this.ws.onmessage = (event) => {
        const data = JSON.parse(event.data)
        this.handleWebSocketMessage(data)
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket 错误:', error)
      }

      this.ws.onclose = () => {
        // WebSocket closed
        this.wsConnected = false
        if (this.isUnmounting) return
        const delay = Math.min(10000, 1000 * Math.pow(2, this.reconnectAttempts))
        this.reconnectAttempts += 1
        this.reconnectTimer = setTimeout(() => {
          this.connectWebSocket()
        }, delay)
      }
    },

    handleWebSocketMessage (data) {
      switch (data.type) {
        case 'user_join':
          this.onlineUsers.push(data.user)
          this.addSystemMessage(`${data.user.username} 加入了会话`)
          break
        case 'user_leave':
          this.onlineUsers = this.onlineUsers.filter(u => u.id !== data.user_id)
          this.addSystemMessage(`用户离开了会话`)
          break
        case 'code_update':
          if (data.user_id !== this.$store.getters.user.id) {
            this.suppressLocalChange = true
            this.setEditorDocument(data.code || '', {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
            this.$nextTick(() => { this.suppressLocalChange = false })
          }
          break
        case 'chat_message':
          this.messages.push(data.message)
          this.$nextTick(() => {
            this.scrollChatToBottom()
          })
          break
        case 'online_users':
          this.onlineUsers = data.users
          this.refreshTokenHolderNameFromOnline()
          break
        case 'relay.status':
          this.relay.tokenHolderId = data.token_holder_id || null
          this.relay.remainingSeconds = typeof data.remaining_seconds === 'number' ? data.remaining_seconds : null
          this.relay.lastStatusTs = Date.now()
          this.relay.waitingQueue = Array.isArray(data.waiting_queue) ? data.waiting_queue : []
          this.syncMyQueuePosition()
          this.refreshTokenHolderNameFromOnline()
          this.refreshEditPermission()
          break
        case 'relay.token_granted':
          this.relay.tokenHolderId = data.token_holder_id || null
          this.relay.tokenHolderName = data.token_holder_name || ''
          this.syncMyQueuePosition()
          this.refreshEditPermission()
          break
        case 'relay.token_transferred':
          this.relay.tokenHolderId = data.new_holder_id || null
          this.syncMyQueuePosition()
          this.refreshTokenHolderNameFromOnline()
          this.refreshEditPermission()
          break
        case 'relay.token_waiting':
          this.relay.queuePosition = data.queue_position || this.relay.queuePosition || null
          this.$info(this.relay.queuePosition ? `已加入队列：第 ${this.relay.queuePosition} 位` : '令牌被占用，请稍候')
          break
        case 'relay.token_cancelled':
          this.relay.cancelling = false
          this.relay.queuePosition = null
          this.$success('已取消排队')
          break
        case 'relay.edit_rejected':
          this.$warning('当前没有输入令牌，已自动切换为只读')
          this.refreshEditPermission()
          break
        case 'scaffolding.init_response':
          if (data.template_code != null && data.template_code !== '' && (!this.code || !this.code.trim())) {
            this.suppressLocalChange = true
            this.setEditorDocument(data.template_code, {
              silent: true,
              cursor: { line: 0, ch: 0 },
              scroll: { left: 0, top: 0 }
            })
            this.$nextTick(() => { this.suppressLocalChange = false })
          }
          break
      }
    },

    changeLanguage () {
      const editorRef = this.$refs.cmEditor
      if (editorRef && this.languageToMode[this.language]) {
        editorRef.setOption('mode', this.languageToMode[this.language])
      }
    },

    runCode () {
      const code = this.code
      if (!code || !code.trim()) {
        this.$warning('请先输入代码')
        return
      }
      this.runResult = { output: '运行中...', time: 0, memory: 0, status: 'running' }
      this.showResultModal = true
      const payload = {
        code: code,
        language: this.language,
        input: ''
      }
      if (this.problemSummary && this.problemSummary.problemId) {
        payload.problem_id = this.problemSummary.problemId
      }
      api.debugCode(payload).then(res => {
        const data = res.data.data
        this.runResult = {
          output: data.output || '无输出',
          error: data.error || '',
          time: data.time_cost != null ? data.time_cost : (data.real_time != null ? data.real_time : 0),
          memory: Math.round(((data.memory_cost != null ? data.memory_cost : (data.memory != null ? data.memory : 0))) / 1024),
          status: data.error ? 'error' : 'success'
        }
      }).catch(() => {
        this.runResult = {
          output: '',
          error: '代码运行服务暂不可用',
          time: 0,
          memory: 0,
          status: 'error'
        }
      })
    },

    resetCode () {
      this.setEditorDocument('', {
        silent: true,
        cursor: { line: 0, ch: 0 },
        scroll: { left: 0, top: 0 }
      })
    },

    sendMessage () {
      if (!this.chatMessage.trim()) return

      if (this.ws && this.ws.readyState === window.WebSocket.OPEN) {
        this.ws.send(JSON.stringify({
          type: 'chat_message',
          text: this.chatMessage
        }))
        this.chatMessage = ''
      } else {
        if (this.ws && this.ws.readyState === window.WebSocket.CONNECTING) {
          this.$warning('聊天连接建立中，请稍后重试')
        } else {
          this.$warning('聊天连接未就绪，正在自动重连')
          this.connectWebSocket()
        }
      }
    },

    addSystemMessage (text) {
      this.messages.push({
        id: Date.now(),
        username: '系统',
        text: text,
        isSystem: true
      })
      this.$nextTick(() => {
        this.scrollChatToBottom()
      })
    },

    scrollChatToBottom () {
      const container = this.$refs.chatMessages
      if (container) {
        container.scrollTop = container.scrollHeight
      }
    },

    transferToken () {
      if (!this.transferTarget) return

      api.transferRelayToken(this.classroomId, this.sessionId, {
        target_user_id: this.transferTarget
      }).then(() => {
        this.$success('令牌已转移')
        this.showTransferModal = false
        this.transferTarget = null
      })
    },

    requestToken () {
      if (!this.ws || this.ws.readyState !== window.WebSocket.OPEN) {
        this.$warning('连接未就绪，正在自动重连')
        this.connectWebSocket()
        return
      }
      this.relay.requesting = true
      this.ws.send(JSON.stringify({
        type: 'relay.request_token',
        user_id: this.$store.getters.user.id
      }))
      setTimeout(() => { this.relay.requesting = false }, 800)
    },

    cancelQueue () {
      if (!this.ws || this.ws.readyState !== window.WebSocket.OPEN) return
      this.relay.cancelling = true
      this.ws.send(JSON.stringify({
        type: 'relay.cancel_request_token',
        user_id: this.$store.getters.user.id
      }))
      setTimeout(() => { this.relay.cancelling = false }, 1200)
    },

    releaseToken () {
      if (!this.ws || this.ws.readyState !== window.WebSocket.OPEN) return
      this.ws.send(JSON.stringify({
        type: 'relay.release_token',
        user_id: this.$store.getters.user.id
      }))
    },

    syncMyQueuePosition () {
      if (this.modeKey !== 'RELAY') {
        this.relay.queuePosition = null
        return
      }
      const uid = Number(this.$store.getters.user.id)
      const q = Array.isArray(this.relay.waitingQueue) ? this.relay.waitingQueue : []
      const idx = q.findIndex(item => Number(item && item.user_id) === uid)
      this.relay.queuePosition = idx >= 0 ? (idx + 1) : null
    },

    startTicker () {
      if (this.ticker) return
      this.ticker = setInterval(() => {
        this.nowTs = Date.now()
      }, 1000)
    },

    refreshTokenHolderNameFromOnline () {
      if (!this.relay.tokenHolderId) {
        this.relay.tokenHolderName = ''
        this.currentTokenHolder = null
        return
      }
      const hit = (this.onlineUsers || []).find(u => Number(u.id) === Number(this.relay.tokenHolderId))
      if (hit) this.relay.tokenHolderName = hit.username || ''
      this.currentTokenHolder = {
        id: Number(this.relay.tokenHolderId),
        username: this.relay.tokenHolderName || ('用户' + this.relay.tokenHolderId)
      }
    },

    refreshEditPermission () {
      const uid = this.$store.getters.user.id
      if (this.modeKey === 'RELAY') {
        this.canEdit = !!this.relay.tokenHolderId && Number(this.relay.tokenHolderId) === Number(uid)
      } else {
        this.canEdit = true
      }
      const editorRef = this.$refs.cmEditor
      if (editorRef) {
        editorRef.setOption('readOnly', !this.canEdit)
      }
    },

    formatRemaining (seconds) {
      if (seconds === null || typeof seconds === 'undefined') return '--:--'
      const s = Math.max(0, Number(seconds) || 0)
      const mm = String(Math.floor(s / 60)).padStart(2, '0')
      const ss = String(s % 60).padStart(2, '0')
      return `${mm}:${ss}`
    },

    openProblemPage () {
      if (!this.problemSummary || !this.problemSummary.problemId) return
      this.$router.push(`/problem/${this.problemSummary.problemId}`)
    },

    endSession () {
      ElMessageBox.confirm(
        '结束后所有用户将退出协作会话，确定继续吗？',
        '确认结束',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.endCollaborationSession(this.classroomId, this.sessionId).then(() => {
          this.$success('会话已结束')
          this.$router.back()
        })
      }).catch(() => {})
    },

    getModeColor (mode) {
      const colors = {
        FREE: 'info',
        RELAY: '',
        SCAFFOLDING: 'info'
      }
      return colors[mode] || 'info'
    },

    getModeText (mode) {
      const texts = {
        FREE: '自由协作',
        RELAY: '代码接力',
        SCAFFOLDING: '编程填空'
      }
      return texts[mode] || mode
    },

    getModeTagStyle (mode) {
      if (mode === 'SCAFFOLDING') {
        return { color: '#000000', borderColor: '#ffe7ba', backgroundColor: '#fff7e6' }
      }
      return {}
    }
  },
  computed: {
    classroomId () {
      return decodeRouteCtx(this.$route.query.ctx).cid || ''
    },
    sessionId () {
      return String(decodeRouteCtx(this.$route.query.ctx).sid || '')
    },
    languageToMode () {
      return {
        python: 'text/x-python',
        cpp: 'text/x-csrc',
        java: 'text/x-java',
        javascript: 'text/javascript'
      }
    },
    modeKey () {
      const m = (this.session && this.session.mode) ? String(this.session.mode) : ''
      return m.toUpperCase()
    },
    floatbarVisible () {
      return true
    },
    floatbarTitle () {
      const p = this.problemSummary && this.problemSummary.title
      if (p) return p
      const t = this.session && this.session.title
      return t || '协作编程'
    },
    tokenHolderDisplay () {
      if (this.modeKey !== 'RELAY') return ''
      if (!this.relay.tokenHolderId) return '无人'
      const uid = Number(this.$store.getters.user.id)
      if (Number(this.relay.tokenHolderId) === uid) return '你'
      return this.relay.tokenHolderName || (this.currentTokenHolder && this.currentTokenHolder.username) || ('用户' + this.relay.tokenHolderId)
    },
    myQueuePosition () {
      return this.modeKey === 'RELAY' ? (this.relay.queuePosition || null) : null
    },
    relayRemainingSeconds () {
      if (this.modeKey !== 'RELAY') return null
      if (typeof this.relay.remainingSeconds !== 'number') return null
      const base = Number(this.relay.remainingSeconds)
      const ts = Number(this.relay.lastStatusTs || 0)
      if (!ts) return base
      const elapsed = Math.floor((this.nowTs - ts) / 1000)
      return Math.max(0, base - Math.max(0, elapsed))
    },
    inputPermissionText () {
      if (this.modeKey === 'RELAY') {
        if (this.relay.tokenHolderId) {
          const name = this.relay.tokenHolderName || (this.currentTokenHolder && this.currentTokenHolder.username) || '某位同学'
          return `${name} 可输入`
        }
        return '无人持有令牌（可申请）'
      }
      if (this.modeKey === 'SCAFFOLDING' && !this.problemSummary) return '未绑定题目，可自由编辑'
      if (this.modeKey === 'SCAFFOLDING') return '允许编辑填空区'
      return '所有在线成员可输入'
    }
  }
}
</script>

<style lang="less" scoped>
.collaborative-coding {
  .collab-floatbar {
    position: sticky;
    top: 0;
    z-index: 50;
    margin-bottom: 14px;
    padding: 10px 12px;
    border-radius: 12px;
    border: 1px solid rgba(220, 222, 226, 0.7);
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.88) 0%, rgba(248, 250, 252, 0.9) 100%);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    box-shadow: 0 10px 26px rgba(15, 23, 42, 0.08), 0 2px 8px rgba(15, 23, 42, 0.05);
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .cf-left {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }
  .cf-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
    background: rgba(45, 140, 240, 0.12);
    color: #2d8cf0;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .cf-title {
    font-weight: 800;
    letter-spacing: 0.2px;
    color: #111827;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 56vw;
  }
  .cf-mode {
    border-radius: 999px;
    flex-shrink: 0;
    color: #17233d;
  }

  .cf-right {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
    flex-shrink: 0;
  }
  .cf-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    border-radius: 999px;
    border: 1px solid rgba(220, 222, 226, 0.8);
    background: rgba(255, 255, 255, 0.8);
    color: #334155;
    font-size: 12px;
    .cf-chip-label {
      color: #64748b;
      font-weight: 600;
    }
    .cf-chip-val {
      font-weight: 800;
      color: #0f172a;
    }
    .mono {
      font-family: 'Courier New', monospace;
      letter-spacing: 0.3px;
    }
  }
  .cf-token {
    border-color: rgba(45, 140, 240, 0.25);
    background: rgba(239, 246, 255, 0.75);
  }
  .cf-timer {
    border-color: rgba(245, 158, 11, 0.28);
    background: rgba(255, 247, 237, 0.8);
  }
  .cf-queue {
    border-color: rgba(99, 102, 241, 0.2);
    background: rgba(238, 242, 255, 0.8);
  }
  .cf-action {
    border-radius: 999px;
    font-weight: 700;
  }
  .cf-cancel {
    padding: 0 6px !important;
    height: 22px !important;
    line-height: 22px !important;
    font-weight: 700;
  }

  .coding-header {
    margin-bottom: 20px;
    
    h3 {
      font-size: 20px;
      margin-bottom: 5px;
    }
    
    .meta {
      color: #808695;
      
      .divider {
        margin: 0 10px;
      }

      .perm {
        color: #515a6e;
      }
    }
  }
  
  .coding-content {
    display: flex;
    align-items: stretch;

    .coding-main-col,
    .coding-side-col {
      display: flex;
      flex-direction: column;
    }

    .editor-card {
      height: 100%;
      display: flex;
      flex-direction: column;

      :deep(.el-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
    }

    .editor-container {
      flex: 1;
      min-height: 0;
      display: flex;
      border: 1px solid #dcdee2;
      border-radius: 4px;
      overflow: hidden;

      :deep(.collab-editor) {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
        margin: 0 !important;
      }

      :deep(.collab-editor .cm5-editor-core) {
        flex: 1;
        min-height: 0;
      }
    }
    
    .editor-toolbar {
      margin-top: 15px;
      display: flex;
      align-items: center;
    }

    .editor-title-right {
      float: right;
    }

    .relay-queue {
      margin-top: 12px;
      padding-top: 10px;
      border-top: 1px dashed #e8eaec;
      .rq-title {
        font-size: 12px;
        color: #808695;
        margin-bottom: 6px;
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .rq-list {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .rq-tag {
        border-radius: 999px;
      }
    }

    .relay-queue-hint {
      margin-top: 10px;
      padding: 9px 12px;
      border-radius: 10px;
      border: 1px solid rgba(59, 130, 246, 0.2);
      background: rgba(239, 246, 255, 0.7);
      color: #1f2937;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      strong {
        font-weight: 800;
      }
    }
    
    .side-panel {
      margin-bottom: 16px;
      
      .user-list {
        max-height: 200px;
        overflow-y: auto;
        
        .user-item {
          display: flex;
          align-items: center;
          padding: 8px 0;
          border-bottom: 1px solid #f0f0f0;
          
          &:last-child {
            border-bottom: none;
          }
          
          .username {
            margin-left: 10px;
            flex: 1;
          }
        }
      }

      &:last-child {
        margin-bottom: 0;
      }
    }
    
    .chat-panel {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;

      :deep(.el-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }

      .chat-messages {
        flex: 1;
        min-height: 360px;
        overflow-y: auto;
        padding: 10px;
        background: #f8f8f9;
        border-radius: 4px;
        margin-bottom: 10px;
        
        .message {
          margin-bottom: 10px;
          
          .message-user {
            font-weight: 600;
            color: #2d8cf0;
            margin-bottom: 3px;
          }
          
          .message-text {
            color: #515a6e;
            word-wrap: break-word;
          }
        }
      }
      
      .chat-input {
        display: flex;
      }
    }

    .problem-panel {
      :deep(.el-card__header) {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .panel-title {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .panel-actions {
        float: right;
      }
      .panel-loading {
        padding: 8px 2px;
        color: #808695;
        display: flex;
        align-items: center;
      }
      .panel-empty {
        padding-top: 8px;
        .panel-empty-hint {
          margin-top: 8px;
          font-size: 12px;
          color: #515a6e;
        }
      }
      .problem-thumb {
        .pt-head {
          display: flex;
          flex-direction: column;
          gap: 6px;
          margin-bottom: 10px;
          .pt-title {
            font-weight: 700;
            color: #1f2937;
            line-height: 1.35;
          }
          .pt-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
          }
        }
        .pt-body {
          max-height: 220px;
          overflow: auto;
          padding: 10px;
          border-radius: 8px;
          background: #f8fafc;
          border: 1px solid #e5e7eb;
        }
        .pt-footer {
          margin-top: 10px;
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
      }

      .problem-collapsed {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
        .pc-title {
          font-weight: 600;
          color: #1f2937;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
  
  .run-result {
    .result-content {
      background: #f8f8f9;
      padding: 15px;
      border-radius: 4px;
      max-height: 400px;
      overflow: auto;
      font-family: 'Courier New', monospace;
      
      &.error {
        background: #fff1f0;
        color: #cf1322;
      }
    }
  }

  .problem-modal-body {
    max-height: 70vh;
    overflow: auto;
    padding-right: 6px;
  }
}
</style>
