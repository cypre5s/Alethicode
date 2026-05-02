<template>
  <div class="view">
    <Panel title="系统路径">
      <div v-loading="loading">
        <el-form label-position="left" label-width="160px" :model="form">
          <el-form-item label="测试用例目录">
            <el-input v-model="form.testCaseDir" placeholder="/data/test_case"/>
            <div class="field-env-hint"><code>TEST_CASE_DIR</code></div>
          </el-form-item>
          <el-form-item label="文件上传目录">
            <el-input v-model="form.uploadDir" placeholder="/data/public/upload"/>
            <div class="field-env-hint"><code>UPLOAD_DIR</code></div>
          </el-form-item>
          <el-form-item label="课程内容包存储根目录">
            <el-input v-model="form.languagePackStorageRoot" placeholder="/data/language_pack"/>
            <div class="field-env-hint"><code>LANGUAGE_PACK_STORAGE_ROOT</code></div>
          </el-form-item>
          <el-form-item label="课程内容包预览目录">
            <el-input v-model="form.languagePackPreviewDir" placeholder="/data/language_pack/preview"/>
            <div class="field-env-hint"><code>LANGUAGE_PACK_PREVIEW_DIR</code></div>
          </el-form-item>
          <el-form-item label="课堂课件目录">
            <el-input v-model="form.classroomLessonDir" placeholder="/data/classroom_lessons"/>
            <div class="field-env-hint"><code>CLASSROOM_LESSON_DIR</code></div>
          </el-form-item>
          <el-form-item label="上传 URL 前缀">
            <el-input v-model="form.uploadPrefix" placeholder="/public/upload"/>
            <div class="field-env-hint"><code>system.upload-prefix</code></div>
          </el-form-item>

          <el-divider/>

          <el-form-item label="强制 HTTPS">
            <el-switch v-model="form.forceHttps"/>
            <span class="switch-hint">{{ form.forceHttps ? '已启用' : '未启用' }}</span>
            <div class="field-env-hint"><code>system.force-https</code></div>
          </el-form-item>
          <el-form-item label="静态 CDN Host">
            <el-input v-model="form.staticCdnHost" placeholder="留空使用本地（如 https://cdn.example.com）"/>
            <div class="field-env-hint"><code>system.static-cdn-host</code></div>
          </el-form-item>

          <el-divider/>

          <el-form-item label="LibreOffice 路径">
            <el-input v-model="form.libreOfficePath" placeholder="libreoffice"/>
            <div class="field-env-hint"><code>language-pack.libre-office-path</code>（课程内容包文档转换）</div>
          </el-form-item>
          <el-form-item label="Python 路径">
            <el-input v-model="form.pythonPath" placeholder="python3"/>
            <div class="field-env-hint"><code>language-pack.python-path</code>（课程内容包脚本执行）</div>
          </el-form-item>
        </el-form>

        <el-button type="primary" :loading="saving" @click="save">保存路径配置</el-button>
      </div>
    </Panel>
  </div>
</template>

<script>
  import api from '../../api.js'
  export default {
    name: 'SecretsSystemPaths',
    data () {
      return {
        loading: false,
        saving: false,
        pathsConfig: { source: '' },
        form: {
          testCaseDir: '',
          uploadDir: '',
          uploadPrefix: '',
          languagePackStorageRoot: '',
          languagePackPreviewDir: '',
          classroomLessonDir: '',
          forceHttps: false,
          staticCdnHost: '',
          libreOfficePath: '',
          pythonPath: ''
        }
      }
    },
    mounted () {
      this.load()
    },
    methods: {
      load () {
        this.loading = true
        api.getSystemPathsConfig().then(res => {
          const d = res.data.data
          this.pathsConfig = d
          this.form.testCaseDir = d.test_case_dir || ''
          this.form.uploadDir = d.upload_dir || ''
          this.form.uploadPrefix = d.upload_prefix || ''
          this.form.languagePackStorageRoot = d.language_pack_storage_root || ''
          this.form.languagePackPreviewDir = d.language_pack_preview_dir || ''
          this.form.classroomLessonDir = d.classroom_lesson_dir || ''
          this.form.forceHttps = d.force_https || false
          this.form.staticCdnHost = d.static_cdn_host || ''
          this.form.libreOfficePath = d.libre_office_path || ''
          this.form.pythonPath = d.python_path || ''
        }).finally(() => {
          this.loading = false
        })
      },
      save () {
        this.saving = true
        api.updateSystemPathsConfig({
          test_case_dir: this.form.testCaseDir,
          upload_dir: this.form.uploadDir,
          upload_prefix: this.form.uploadPrefix,
          language_pack_storage_root: this.form.languagePackStorageRoot,
          language_pack_preview_dir: this.form.languagePackPreviewDir,
          classroom_lesson_dir: this.form.classroomLessonDir,
          force_https: this.form.forceHttps,
          static_cdn_host: this.form.staticCdnHost,
          libre_office_path: this.form.libreOfficePath,
          python_path: this.form.pythonPath
        }).then(() => {
          this.load()
        }).finally(() => {
          this.saving = false
        })
      }
    }
  }
</script>

<style scoped lang="less">
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

  .switch-hint {
    margin-left: 10px;
    font-size: 13px;
    color: var(--admin-text-muted);
  }
</style>
