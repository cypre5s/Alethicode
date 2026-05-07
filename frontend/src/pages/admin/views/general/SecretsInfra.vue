<template>
  <div class="view">
    <Panel title="数据库与基础设施">
      <div v-if="envSnapshot" class="infra-grid">
        <div class="infra-card">
          <div class="infra-card__title">PostgreSQL</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">连接 URL</span>
              <code class="kv-value">{{ envSnapshot.db_url }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">密码状态</span>
              <el-tag :type="envSnapshot.db_password_set ? 'success' : 'warning'" effect="plain" size="small">
                {{ envSnapshot.db_password_set ? '已设置' : '未设置' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">Redis</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">Host</span>
              <code class="kv-value">{{ envSnapshot.redis_host }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">Port</span>
              <code class="kv-value">{{ envSnapshot.redis_port }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">密码状态</span>
              <el-tag :type="envSnapshot.redis_password_set ? 'success' : 'warning'" effect="plain" size="small">
                {{ envSnapshot.redis_password_set ? '已设置' : '未设置' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">判题服务</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">Server Token</span>
              <el-tag :type="envSnapshot.judge_server_token_set ? 'success' : 'danger'" effect="plain" size="small">
                {{ envSnapshot.judge_server_token_set ? '已设置' : '未设置（必填）' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">LLM 运行时</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">后端模式</span>
              <el-tag type="success" effect="plain" size="small">LangGraph + Langfuse</el-tag>
            </div>
            <div class="kv-row">
              <span class="kv-label">Langfuse</span>
              <el-tag :type="envSnapshot.langfuse_enabled ? 'success' : 'warning'" effect="plain" size="small">
                {{ envSnapshot.langfuse_enabled ? '已配置' : '未配置' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">视频生成（Beta）</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">TTS 提供商</span>
              <code class="kv-value">{{ envSnapshot.video_tts_provider }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">渲染提供商</span>
              <code class="kv-value">{{ envSnapshot.video_render_provider }}</code>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">Temporal</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">启用状态</span>
              <el-tag :type="envSnapshot.temporal_enabled ? 'success' : 'warning'" effect="plain" size="small">
                {{ envSnapshot.temporal_enabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
            <div class="kv-row">
              <span class="kv-label">Namespace</span>
              <code class="kv-value">{{ envSnapshot.temporal_namespace || '(not set)' }}</code>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">Unleash</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">API</span>
              <code class="kv-value">{{ infraSecrets.unleash_api_url || '(not set)' }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">Token</span>
              <el-tag :type="infraSecrets.unleash_api_key_set ? 'success' : 'warning'" effect="plain" size="small">
                {{ infraSecrets.unleash_api_key_set ? '已设置' : '未设置' }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">NATS JetStream</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">传输</span>
              <code class="kv-value">{{ envSnapshot.judge_dispatch_transport }}</code>
            </div>
            <div class="kv-row">
              <span class="kv-label">URL</span>
              <code class="kv-value">{{ infraSecrets.nats_url || '(not set)' }}</code>
            </div>
          </div>
        </div>

        <div class="infra-card">
          <div class="infra-card__title">FSRS</div>
          <div class="kv-list">
            <div class="kv-row">
              <span class="kv-label">启用状态</span>
              <el-tag :type="envSnapshot.fsrs_enabled ? 'success' : 'warning'" effect="plain" size="small">
                {{ envSnapshot.fsrs_enabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
            <div class="kv-row">
              <span class="kv-label">目标保留率</span>
              <code class="kv-value">{{ envSnapshot.fsrs_desired_retention }}</code>
            </div>
          </div>
        </div>
      </div>

      <el-skeleton v-else :rows="4" animated style="margin-top:8px"/>
    </Panel>
    <Panel title="凭据管理">
      <el-form label-position="left" label-width="160px" :model="secretsForm">
        <h4 class="sub-section-title">PostgreSQL</h4>
        <el-row :gutter="20">
          <el-col :span="14">
            <el-form-item label="连接 URL">
              <el-input v-model="secretsForm.dbUrl" placeholder="jdbc:postgresql://127.0.0.1:5436/alethicode"/>
              <div class="field-env-hint"><code>spring.datasource.url</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="用户名">
              <el-input v-model="secretsForm.dbUsername" placeholder="onlinejudge"/>
              <div class="field-env-hint"><code>spring.datasource.username</code></div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="密码">
          <el-input
            v-model="secretsForm.dbPassword"
            :type="showDbPwd ? 'text' : 'password'"
            :placeholder="infraSecrets.db_password_set ? '已配置（输入新值覆盖）' : '输入数据库密码'"
            autocomplete="new-password">
            <template #prefix>
              <el-icon v-if="infraSecrets.db_password_set && !secretsForm.dbPassword" color="#10b981" style="cursor:default"><CircleCheckFilled /></el-icon>
              <el-icon v-else><Key /></el-icon>
            </template>
            <template #suffix>
              <el-icon style="cursor:pointer" @click="showDbPwd = !showDbPwd">
                <View v-if="!showDbPwd"/><Hide v-else/>
              </el-icon>
            </template>
          </el-input>
          <div class="field-env-hint"><code>DB_PASSWORD</code></div>
        </el-form-item>

        <h4 class="sub-section-title">Redis</h4>
        <el-row :gutter="20">
          <el-col :span="14">
            <el-form-item label="Host">
              <el-input v-model="secretsForm.redisHost" placeholder="127.0.0.1"/>
              <div class="field-env-hint"><code>REDIS_HOST</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="Port">
              <el-input-number v-model="secretsForm.redisPort" :min="1" :max="65535" style="width:100%"/>
              <div class="field-env-hint"><code>REDIS_PORT</code></div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="密码">
          <el-input
            v-model="secretsForm.redisPassword"
            :type="showRedisPwd ? 'text' : 'password'"
            :placeholder="infraSecrets.redis_password_set ? '已配置（输入新值覆盖）' : '输入 Redis 密码'"
            autocomplete="new-password">
            <template #prefix>
              <el-icon v-if="infraSecrets.redis_password_set && !secretsForm.redisPassword" color="#10b981" style="cursor:default"><CircleCheckFilled /></el-icon>
              <el-icon v-else><Key /></el-icon>
            </template>
            <template #suffix>
              <el-icon style="cursor:pointer" @click="showRedisPwd = !showRedisPwd">
                <View v-if="!showRedisPwd"/><Hide v-else/>
              </el-icon>
            </template>
          </el-input>
          <div class="field-env-hint"><code>REDIS_PASSWORD</code></div>
        </el-form-item>

        <h4 class="sub-section-title">判题服务</h4>
        <el-form-item label="Server Token">
          <el-input
            v-model="secretsForm.judgeServerToken"
            :type="showJudgeToken ? 'text' : 'password'"
            :placeholder="infraSecrets.judge_server_token_set ? (infraSecrets.judge_server_token_masked || '已配置（输入新值覆盖）') : '输入 Judge Server Token'"
            autocomplete="new-password">
            <template #prefix>
              <el-icon v-if="infraSecrets.judge_server_token_set && !secretsForm.judgeServerToken" color="#10b981" style="cursor:default"><CircleCheckFilled /></el-icon>
              <el-icon v-else><Key /></el-icon>
            </template>
            <template #suffix>
              <el-icon style="cursor:pointer" @click="showJudgeToken = !showJudgeToken">
                <View v-if="!showJudgeToken"/><Hide v-else/>
              </el-icon>
            </template>
          </el-input>
          <div class="field-env-hint"><code>JUDGE_SERVER_TOKEN</code></div>
        </el-form-item>

        <h4 class="sub-section-title">Temporal</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="Target">
              <el-input v-model="secretsForm.temporalTarget" placeholder="127.0.0.1:7233"/>
              <div class="field-env-hint"><code>TEMPORAL_TARGET</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Namespace">
              <el-input v-model="secretsForm.temporalNamespace" placeholder="default"/>
              <div class="field-env-hint"><code>TEMPORAL_NAMESPACE</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Task Queue">
              <el-input v-model="secretsForm.temporalTaskQueue" placeholder="language-pack-pipeline"/>
              <div class="field-env-hint"><code>TEMPORAL_TASK_QUEUE</code></div>
            </el-form-item>
          </el-col>
        </el-row>

        <h4 class="sub-section-title">Unleash</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="API URL">
              <el-input v-model="secretsForm.unleashApiUrl" placeholder="http://127.0.0.1:4242/api"/>
              <div class="field-env-hint"><code>UNLEASH_API_URL</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Project">
              <el-input v-model="secretsForm.unleashProject" placeholder="ai-tutor"/>
              <div class="field-env-hint"><code>UNLEASH_PROJECT</code></div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Token">
          <el-input
            v-model="secretsForm.unleashApiKey"
            type="password"
            :placeholder="infraSecrets.unleash_api_key_set ? '已配置（输入新值覆盖）' : '输入 Unleash Client Token'"
            autocomplete="new-password"/>
          <div class="field-env-hint"><code>UNLEASH_API_KEY</code></div>
        </el-form-item>

        <h4 class="sub-section-title">NATS JetStream</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="NATS URL">
              <el-input v-model="secretsForm.natsUrl" placeholder="nats://127.0.0.1:4222"/>
              <div class="field-env-hint"><code>NATS_URL</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Stream">
              <el-input v-model="secretsForm.natsStreamName" placeholder="ALETHICODE_JUDGE"/>
              <div class="field-env-hint"><code>NATS_JUDGE_STREAM</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Subject">
              <el-input v-model="secretsForm.natsSubject" placeholder="judge.dispatch"/>
              <div class="field-env-hint"><code>NATS_JUDGE_SUBJECT</code></div>
            </el-form-item>
          </el-col>
        </el-row>

        <h4 class="sub-section-title">Langfuse</h4>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="Base URL">
              <el-input v-model="secretsForm.langfuseBaseUrl" placeholder="http://127.0.0.1:3000"/>
              <div class="field-env-hint"><code>LANGFUSE_BASE_URL</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Environment">
              <el-input v-model="secretsForm.langfuseTracingEnvironment" placeholder="production"/>
              <div class="field-env-hint"><code>LANGFUSE_TRACING_ENVIRONMENT</code></div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="Public Key">
              <el-input v-model="secretsForm.langfusePublicKey" type="password" :placeholder="infraSecrets.langfuse_public_key_set ? '已配置（输入新值覆盖）' : '输入 Langfuse Public Key'" autocomplete="new-password"/>
              <div class="field-env-hint"><code>LANGFUSE_PUBLIC_KEY</code></div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Secret Key">
              <el-input v-model="secretsForm.langfuseSecretKey" type="password" :placeholder="infraSecrets.langfuse_secret_key_set ? '已配置（输入新值覆盖）' : '输入 Langfuse Secret Key'" autocomplete="new-password"/>
              <div class="field-env-hint"><code>LANGFUSE_SECRET_KEY</code></div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-button type="primary" :loading="secretsSaving" @click="saveSecrets">保存凭据</el-button>
    </Panel>
  </div>
</template>

<script>
  import api from '../../api.js'
  import { Key, View, Hide, CircleCheckFilled } from '@element-plus/icons-vue'

  export default {
    name: 'SecretsInfra',
    components: { Key, View, Hide, CircleCheckFilled },
    data () {
      return {
        envSnapshot: null,
        infraSecrets: { source: '' },
        secretsForm: {
          dbUrl: '', dbUsername: '', dbPassword: '',
          redisHost: '', redisPort: 6381, redisPassword: '',
          judgeServerToken: '',
          temporalTarget: '',
          temporalNamespace: '',
          temporalTaskQueue: '',
          unleashApiUrl: '',
          unleashApiKey: '',
          unleashProject: '',
          natsUrl: '',
          natsStreamName: '',
          natsSubject: '',
          langfuseBaseUrl: '',
          langfusePublicKey: '',
          langfuseSecretKey: '',
          langfuseTracingEnvironment: 'production'
        },
        showDbPwd: false,
        showRedisPwd: false,
        showJudgeToken: false,
        secretsSaving: false
      }
    },
    mounted () {
      api.getEnvSnapshot().then(res => {
        this.envSnapshot = res.data.data
      })
      api.getInfraSecrets().then(res => {
        const d = res.data.data
        this.infraSecrets = d
        this.secretsForm.dbUrl = d.db_url || ''
            this.secretsForm.dbUsername = d.db_username || ''
            this.secretsForm.redisHost = d.redis_host || ''
            this.secretsForm.redisPort = d.redis_port || 6381
            this.secretsForm.temporalTarget = d.temporal_target || ''
            this.secretsForm.temporalNamespace = d.temporal_namespace || ''
            this.secretsForm.temporalTaskQueue = d.temporal_task_queue || ''
            this.secretsForm.unleashApiUrl = d.unleash_api_url || ''
            this.secretsForm.unleashProject = d.unleash_project || ''
            this.secretsForm.natsUrl = d.nats_url || ''
            this.secretsForm.natsStreamName = d.nats_stream_name || ''
            this.secretsForm.natsSubject = d.nats_subject || ''
            this.secretsForm.langfuseBaseUrl = d.langfuse_base_url || ''
            this.secretsForm.langfuseTracingEnvironment = d.langfuse_tracing_environment || 'production'
          })
        },
        methods: {
      saveSecrets () {
        this.secretsSaving = true
            api.updateInfraSecrets({
              db_url: this.secretsForm.dbUrl || null,
              db_username: this.secretsForm.dbUsername || null,
              db_password: this.secretsForm.dbPassword || null,
              redis_host: this.secretsForm.redisHost || null,
              redis_port: this.secretsForm.redisPort || null,
              redis_password: this.secretsForm.redisPassword || null,
              judge_server_token: this.secretsForm.judgeServerToken || null,
              temporal_target: this.secretsForm.temporalTarget || null,
              temporal_namespace: this.secretsForm.temporalNamespace || null,
              temporal_task_queue: this.secretsForm.temporalTaskQueue || null,
              unleash_api_url: this.secretsForm.unleashApiUrl || null,
              unleash_api_key: this.secretsForm.unleashApiKey || null,
              unleash_project: this.secretsForm.unleashProject || null,
              nats_url: this.secretsForm.natsUrl || null,
              nats_stream_name: this.secretsForm.natsStreamName || null,
              nats_subject: this.secretsForm.natsSubject || null,
              langfuse_base_url: this.secretsForm.langfuseBaseUrl || null,
              langfuse_public_key: this.secretsForm.langfusePublicKey || null,
              langfuse_secret_key: this.secretsForm.langfuseSecretKey || null,
              langfuse_tracing_environment: this.secretsForm.langfuseTracingEnvironment || null
            }).then(() => {
              this.$message.success('凭据已保存，重启后生效')
              this.secretsForm.dbPassword = ''
              this.secretsForm.redisPassword = ''
              this.secretsForm.judgeServerToken = ''
              this.secretsForm.unleashApiKey = ''
              this.secretsForm.langfusePublicKey = ''
              this.secretsForm.langfuseSecretKey = ''
              api.getInfraSecrets().then(res => {
                const d = res.data.data
                this.infraSecrets = d
                this.secretsForm.dbUrl = d.db_url || ''
                this.secretsForm.dbUsername = d.db_username || ''
                this.secretsForm.redisHost = d.redis_host || ''
                this.secretsForm.redisPort = d.redis_port || 6381
                this.secretsForm.temporalTarget = d.temporal_target || ''
                this.secretsForm.temporalNamespace = d.temporal_namespace || ''
                this.secretsForm.temporalTaskQueue = d.temporal_task_queue || ''
                this.secretsForm.unleashApiUrl = d.unleash_api_url || ''
                this.secretsForm.unleashProject = d.unleash_project || ''
                this.secretsForm.natsUrl = d.nats_url || ''
                this.secretsForm.natsStreamName = d.nats_stream_name || ''
                this.secretsForm.natsSubject = d.nats_subject || ''
                this.secretsForm.langfuseBaseUrl = d.langfuse_base_url || ''
                this.secretsForm.langfuseTracingEnvironment = d.langfuse_tracing_environment || 'production'
              })
            }).finally(() => {
              this.secretsSaving = false
        })
      }
    }
  }
</script>

<style scoped lang="less">
  .sub-section-title {
    margin: 16px 0 14px;
    padding-bottom: 10px;
    font-size: 14px;
    font-weight: 600;
    color: #334155;
    border-bottom: 1px solid rgba(148, 163, 184, 0.18);

    &:first-child {
      margin-top: 0;
    }
  }

  .field-env-hint {
    margin-top: 4px;
    font-size: 11px;
    color: var(--admin-text-muted);

    code {
      background: rgba(37, 99, 235, 0.06);
      padding: 1px 6px;
      border-radius: 4px;
      font-family: var(--font-mono);
      font-size: 11px;
      color: #2563eb;
    }
  }

  .view {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }

  .banner-content {
    flex: 1;
    min-width: 0;
  }

  .infra-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 14px;
  }

  .infra-card {
    padding: 18px 20px;
    border-radius: 14px;
    background: #fff;
    border: 1px solid rgba(148, 163, 184, 0.18);
    transition: border-color 0.2s;

    &:hover {
      border-color: rgba(37, 99, 235, 0.2);
    }

    &__title {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      margin-bottom: 14px;
      padding-bottom: 10px;
      border-bottom: 1px solid rgba(148, 163, 184, 0.14);
    }
  }

  .kv-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .kv-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-width: 0;
  }

  .kv-label {
    font-size: 13px;
    color: var(--admin-text-muted);
    flex-shrink: 0;
  }

  .kv-value {
    font-family: var(--font-mono);
    font-size: 12px;
    color: #0f172a;
    text-align: right;
    word-break: break-all;
  }
</style>
