<template>
  <div class="view">
    <Panel title="AI 服务配置">
      <div v-loading="aiLoading">
        <h4 class="sub-section-title">大模型（LLM）</h4>

        <el-form label-position="left" label-width="150px" :model="aiForm">
          <input type="text" name="prevent-autofill" autocomplete="username" style="display:none" aria-hidden="true" tabindex="-1"/>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="API Key">
                <el-input
                  v-model="aiForm.apiKey"
                  :type="showApiKey ? 'text' : 'password'"
                  :placeholder="aiConfig.api_key_set ? '已配置（输入新值覆盖）' : '输入 API Key'"
                  autocomplete="new-password">
                  <template #prefix>
                    <el-icon v-if="aiConfig.api_key_set && !aiForm.apiKey" color="#10b981" style="cursor:default">
                      <CircleCheckFilled />
                    </el-icon>
                    <el-icon v-else><Key /></el-icon>
                  </template>
                  <template #suffix>
                    <el-icon style="cursor:pointer" @click="showApiKey = !showApiKey">
                      <View v-if="!showApiKey"/>
                      <Hide v-else/>
                    </el-icon>
                  </template>
                </el-input>
                <div class="field-env-hint"><code>OPENAI_API_KEY</code></div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Base URL">
                <el-input v-model="aiForm.baseUrl" placeholder="https://api.deepseek.com"/>
                <div class="field-env-hint"><code>LLM_BASE_URL</code></div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="模型名称">
                <el-input v-model="aiForm.model" placeholder="deepseek-v4-flash"/>
                <div class="field-env-hint"><code>LLM_MODEL</code></div>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="超时（秒）">
                <el-input-number v-model="aiForm.timeoutSeconds" :min="10" :max="600" style="width:100%"/>
                <div class="field-env-hint"><code>LLM_API_TIMEOUT_SECONDS</code></div>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="最大重试次数">
                <el-input-number v-model="aiForm.maxRetries" :min="0" :max="20" style="width:100%"/>
                <div class="field-env-hint"><code>LLM_API_MAX_RETRIES</code></div>
              </el-form-item>
            </el-col>
          </el-row>

          <h4 class="sub-section-title" style="margin-top:8px">向量嵌入（Embedding）</h4>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="Embedding API Key">
                <el-input
                  v-model="aiForm.embeddingApiKey"
                  :type="showEmbKey ? 'text' : 'password'"
                  :placeholder="aiConfig.embedding_api_key_set ? '已配置（输入新值覆盖）' : '留空则复用主 API Key'"
                  autocomplete="new-password">
                  <template #prefix>
                    <el-icon v-if="aiConfig.embedding_api_key_set && !aiForm.embeddingApiKey" color="#10b981" style="cursor:default">
                      <CircleCheckFilled />
                    </el-icon>
                    <el-icon v-else><Key /></el-icon>
                  </template>
                  <template #suffix>
                    <el-icon style="cursor:pointer" @click="showEmbKey = !showEmbKey">
                      <View v-if="!showEmbKey"/>
                      <Hide v-else/>
                    </el-icon>
                  </template>
                </el-input>
                <div class="field-env-hint"><code>EMBEDDING_API_KEY</code></div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Embedding URL">
                <el-input v-model="aiForm.embeddingBaseUrl" placeholder="https://api.openai.com/v1"/>
                <div class="field-env-hint"><code>EMBEDDING_BASE_URL</code></div>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="Embedding 模型">
                <el-input v-model="aiForm.embeddingModel" placeholder="text-embedding-3-small"/>
                <div class="field-env-hint"><code>EMBEDDING_MODEL</code></div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-button type="primary" :loading="aiSaving" @click="saveAiConfig">保存 AI 配置</el-button>
      </div>
    </Panel>
  </div>
</template>

<script>
  import api from '../../api.js'
  import { Key, View, Hide, CircleCheckFilled } from '@element-plus/icons-vue'

  export default {
    name: 'SecretsAiConfig',
    components: { Key, View, Hide, CircleCheckFilled },
    data () {
      return {
        aiLoading: false,
        aiSaving: false,
        showApiKey: false,
        showEmbKey: false,
        aiConfig: {
          apiKeyMasked: '',
          apiKeySet: false,
          baseUrl: '',
          model: '',
          embeddingApiKeyMasked: '',
          embeddingApiKeySet: false,
          embeddingBaseUrl: '',
          embeddingModel: '',
          timeoutSeconds: 150,
          maxRetries: 3,
          source: ''
        },
        aiForm: {
          apiKey: '',
          baseUrl: '',
          model: '',
          embeddingApiKey: '',
          embeddingBaseUrl: '',
          embeddingModel: '',
          timeoutSeconds: 150,
          maxRetries: 3
        }
      }
    },
    mounted () {
      this.loadAiConfig()
    },
    methods: {
      loadAiConfig () {
        this.aiLoading = true
        api.getAiProviderConfig().then(res => {
          this.aiConfig = res.data.data
          this.aiForm.baseUrl = this.aiConfig.base_url
          this.aiForm.model = this.aiConfig.model
          this.aiForm.embeddingBaseUrl = this.aiConfig.embedding_base_url
          this.aiForm.embeddingModel = this.aiConfig.embedding_model
          this.aiForm.timeoutSeconds = this.aiConfig.timeout_seconds
          this.aiForm.maxRetries = this.aiConfig.max_retries
        }).finally(() => {
          this.aiLoading = false
        })
      },
      saveAiConfig () {
        this.aiSaving = true
        api.updateAiProviderConfig({
          apiKey: this.aiForm.apiKey || null,
          baseUrl: this.aiForm.baseUrl,
          model: this.aiForm.model,
          embeddingApiKey: this.aiForm.embeddingApiKey || null,
          embeddingBaseUrl: this.aiForm.embeddingBaseUrl,
          embeddingModel: this.aiForm.embeddingModel,
          timeoutSeconds: this.aiForm.timeoutSeconds,
          maxRetries: this.aiForm.maxRetries
        }).then(() => {
          this.$message.success('AI 配置已保存')
          this.aiForm.apiKey = ''
          this.aiForm.embeddingApiKey = ''
          this.loadAiConfig()
        }).catch(() => {
          this.$message.error('保存失败，请检查输入')
        }).finally(() => {
          this.aiSaving = false
        })
      }
    }
  }
</script>

<style scoped lang="less">
  .sub-section-title {
    margin: 0 0 16px;
    padding-bottom: 10px;
    font-size: 14px;
    font-weight: 600;
    color: #334155;
    border-bottom: 1px solid rgba(148, 163, 184, 0.18);
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
</style>
