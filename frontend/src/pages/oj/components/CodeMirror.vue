<template>
  <div style="margin: 0px 0px 15px 0px">
    <div v-if="!hideHeader" class="header">
      <div class="header-left">
        <span class="lang-label">{{ $t('m.Language') }}</span>
        <el-select :model-value="language" @update:modelValue="onLangChange" class="adjust" size="small">
          <el-option v-for="item in languages" :key="item" :value="item" :label="item" />
        </el-select>
      </div>
      <div class="header-right">
        <el-button @click="onResetClick" size="small" aria-label="重置代码">
          <el-icon :size="14"><Refresh /></el-icon>
        </el-button>

        <el-button @click="onUploadFile" size="small" aria-label="上传代码文件">
          <el-icon :size="14"><Upload /></el-icon>
        </el-button>

        <input
          id="file-uploader"
          type="file"
          style="display: none"
          @change="onUploadFileDone"
          accept=".c,.cpp,.cc,.cxx,.h,.hpp,.java,.py,.go,.js,.ts,.rs,.rb,.php,.cs,.swift,.kt,.scala,.r,.m,.pl,.sh,.sql,.txt">

        <slot name="extra-tools"></slot>
      </div>
    </div>
    <Cm5EditorCore
      ref="editorCore"
      :initial-value="initialValue"
      :options="options"
      @change="onEditorCodeChange"
      @submit="$emit('submit')"
      @debug="$emit('debug')">
    </Cm5EditorCore>
  </div>
</template>
<script>
  import utils from '@/utils/utils'
  import Cm5EditorCore from '@/components/Cm5EditorCore.vue'
  import { Refresh, Upload } from '@element-plus/icons-vue'

  export default {
    name: 'CodeMirror',
    components: {
      Cm5EditorCore,
      Refresh,
      Upload
    },
    props: {
      initialValue: {
        type: String,
        default: ''
      },
      languages: {
        type: Array,
        default: () => []
      },
      language: {
        type: String,
        default: ''
      },
      theme: {
        type: String,
        default: 'solarized'
      },
      hideHeader: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        options: {
          tabSize: 4,
          mode: 'text/plain',
          completionProfile: '',
          theme: 'solarized',
          lineNumbers: true,
          line: true,
          foldGutter: {
            rangeFinder: function (cm, pos) {
              var helpers = cm.getHelpers(pos, 'fold')
              for (var i = 0; i < helpers.length; i++) {
                try {
                  var range = helpers[i](cm, pos)
                  if (range) return range
                } catch (e) {
                  console.warn('[CodeMirror] fold helper failed:', e)
                }
              }
              return null
            }
          },
          gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter', 'hotspot-gutter'],
          styleSelectedText: true,
          lineWrapping: true,
          styleActiveLine: true,
          highlightSelectionMatches: {showToken: /\w/, annotateScrollbar: true}
        },
        mode: {
          'C++': 'text/x-c++src'
        },
        _apMarks: [],
        _errorHighlights: []
      }
    },
    computed: {
      editor () {
        const core = this.$refs.editorCore
        return core ? core.editor : null
      },
      core () {
        return this.$refs.editorCore || null
      }
    },
    mounted () {
      utils.getLanguages().then(languages => {
        const nextModeMap = {}
        languages.forEach(lang => {
          if (typeof lang === 'string') {
            nextModeMap[lang] = this.getModeByLanguage(lang)
            return
          }
          if (lang && lang.name) {
            nextModeMap[lang.name] = lang.content_type || this.getModeByLanguage(lang.name)
          }
        })
        this.mode = Object.assign({}, this.mode, nextModeMap)
        this.syncLanguageOptions(this.language)
      })
      this.focus()
    },
    beforeUnmount () {
      this.clearAntiPatterns()
      this.clearErrorHighlights()
    },
    watch: {
      language (newVal) {
        this.syncLanguageOptions(newVal)
      }
    },
    methods: {
      syncLanguageOptions (language) {
        this.setOption('mode', this.mode[language] || this.getModeByLanguage(language))
        this.setOption('completionProfile', language)
      },
      getModeByLanguage (language) {
        const map = {
          C: 'text/x-csrc',
          'C++': 'text/x-c++src',
          Java: 'text/x-java',
          Python2: 'text/x-python',
          Python3: 'text/x-python',
          Python: 'text/x-python',
          JavaScript: 'text/javascript',
          Go: 'text/x-go'
        }
        return map[language] || 'text/plain'
      },
      getValidOneBasedLineNumber (rawLineNumber) {
        const lineNumber = Number(rawLineNumber)
        if (!Number.isInteger(lineNumber) || lineNumber < 1) {
          return null
        }
        return lineNumber
      },
      getValidEditorCode (rawCode) {
        return typeof rawCode === 'string' ? rawCode : null
      },
      getCore () {
        return this.$refs.editorCore || null
      },
      setDocument (text, config = {}) {
        const core = this.getCore()
        if (core && typeof core.setDocument === 'function') {
          core.setDocument(text, config)
        }
      },
      getDocument () {
        const core = this.getCore()
        if (core && typeof core.getDocument === 'function') {
          return core.getDocument()
        }
        return typeof this.initialValue === 'string' ? this.initialValue : ''
      },
      setOption (name, value) {
        const core = this.getCore()
        if (core && typeof core.setOption === 'function') {
          core.setOption(name, value)
        }
      },
      focus () {
        const core = this.getCore()
        if (core && typeof core.focus === 'function') {
          core.focus()
        }
      },
      refreshEditorLayout () {
        const core = this.getCore()
        if (core && typeof core.refreshLayout === 'function') {
          core.refreshLayout()
        }
      },
      finalizeCompositionState () {
      },
      onEditorCodeChange (newCode) {
        this.$emit('change', newCode)
      },
      onLangChange (newVal) {
        this.$emit('changeLang', newVal)
        this.$nextTick(() => this.focus())
      },
      onResetClick () {
        this.$emit('resetCode')
      },
      onUploadFile () {
        const input = document.getElementById('file-uploader')
        if (input) {
          input.click()
        }
      },
      applyAntiPatterns (antiPatterns) {
        this.clearAntiPatterns()
        if (!antiPatterns || !antiPatterns.length || !this.core) return
        const c = this.core
        antiPatterns.forEach(ap => {
          const lineNumber = this.getValidOneBasedLineNumber(ap && ap.line)
          if (lineNumber === null) return
          const line = lineNumber - 1
          if (line >= c.lineCount()) return

          const severityClass = {
            critical: 'ap-squiggle-critical',
            high: 'ap-squiggle-high',
            medium: 'ap-squiggle-medium',
            low: 'ap-squiggle-low'
          }[ap.severity] || 'ap-squiggle-medium'

          const lineContent = c.getLine(line)
          if (typeof lineContent !== 'string') return
          const lineLen = lineContent.length
          if (lineLen > 0) {
            const mark = c.markText(
              { line, ch: 0 },
              { line, ch: lineLen },
              { className: severityClass, title: ap.hint }
            )
            this._apMarks.push(mark)
          }

          const gutterEl = document.createElement('div')
          gutterEl.className = 'ap-gutter-marker'
          gutterEl.title = `[${ap.severity}] ${ap.type}: ${ap.hint}`
          const dot = document.createElement('span')
          dot.className = 'ap-dot ap-dot-' + ap.severity
          gutterEl.appendChild(dot)
          c.setGutterMarker(line, 'hotspot-gutter', gutterEl)
        })
      },
      clearAntiPatterns () {
        this._apMarks.forEach(mark => mark.clear())
        this._apMarks = []
        if (this.core) {
          this.core.clearGutter('hotspot-gutter')
        }
      },
      highlightErrorLines (lines) {
        this.clearErrorHighlights()
        if (!this.core) return
        const c = this.core
        lines.forEach(({ line, message, severity }) => {
          const rawLineNumber = Number(line)
          if (!Number.isInteger(rawLineNumber) || rawLineNumber < 1) return
          const lineNum = rawLineNumber - 1
          if (lineNum >= c.lineCount()) return
          const cls = severity === 'error' ? 'ai-error-line' : 'ai-warning-line'
          c.addLineClass(lineNum, 'background', cls)
          this._errorHighlights.push({ line: lineNum, cls })
          const el = document.createElement('div')
          el.className = 'ai-gutter-icon ai-gutter-' + severity
          el.title = message
          el.innerHTML = severity === 'error' ? '●' : '▲'
          c.setGutterMarker(lineNum, 'hotspot-gutter', el)
        })
        if (this._errorHighlights.length > 0) {
          c.scrollToLine(this._errorHighlights[0].line, 100)
        }
      },
      clearErrorHighlights () {
        if (!this.core) {
          this._errorHighlights = []
          return
        }
        this._errorHighlights.forEach(({ line, cls }) => {
          this.core.removeLineClass(line, 'background', cls)
        })
        this._errorHighlights = []
        this.core.clearGutter('hotspot-gutter')
      },
      insertCodeAtCursor (codeText) {
        const validCodeText = this.getValidEditorCode(codeText)
        if (validCodeText === null) {
          throw new Error('insertCodeAtCursor requires a string payload')
        }
        const core = this.getCore()
        if (core && typeof core.insertCodeAtCursor === 'function') {
          core.insertCodeAtCursor(validCodeText)
        }
      },
      replaceLines (startLine, endLine, newCode) {
        const startLineNumber = Number(startLine)
        const endLineNumber = Number(endLine)
        if (!Number.isInteger(startLineNumber) || !Number.isInteger(endLineNumber)) {
          throw new Error('replaceLines requires integer line numbers')
        }
        if (startLineNumber < 1 || endLineNumber < startLineNumber) {
          throw new Error('replaceLines range is invalid')
        }
        const validNewCode = this.getValidEditorCode(newCode)
        if (validNewCode === null) {
          throw new Error('replaceLines requires a string payload')
        }
        const core = this.getCore()
        if (core && typeof core.replaceLines === 'function') {
          core.replaceLines(startLineNumber, endLineNumber, validNewCode)
        }
      },
      appendCode (codeText) {
        const validCodeText = this.getValidEditorCode(codeText)
        if (validCodeText === null) {
          throw new Error('appendCode requires a string payload')
        }
        const core = this.getCore()
        if (core && typeof core.appendCode === 'function') {
          core.appendCode(validCodeText)
        }
      },
      onUploadFileDone () {
        const input = document.getElementById('file-uploader')
        const file = input && input.files ? input.files[0] : null
        if (!file) return

        if (file.size > 256 * 1024) {
          this.$error('文件过大，请上传 256KB 以内的代码文件')
          input.value = ''
          return
        }

        const fileReader = new window.FileReader()
        fileReader.onload = (e) => {
          const text = e && e.target ? e.target.result : ''
          if (text && text.indexOf('\0') !== -1) {
            this.$error('不支持的文件格式，请上传纯文本代码文件')
            input.value = ''
            return
          }
          this.setDocument(text, { cursor: { line: 0, ch: 0 }, scroll: { left: 0, top: 0 } })
          this.$success('文件已加载: ' + file.name)
          input.value = ''
        }
        fileReader.onerror = () => {
          this.$error('文件读取失败')
          input.value = ''
        }
        fileReader.readAsText(file, 'UTF-8')
      }
    }
  }
</script>

<style lang="less" scoped>
  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 5px 5px 15px 5px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .lang-label {
        font-size: 13px;
        color: var(--text-secondary, #606266);
        white-space: nowrap;
      }

      .adjust {
        width: 150px;
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
</style>

<style>
</style>
