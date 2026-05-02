<template>
  <Cm5EditorCore
    ref="editorCore"
    :initial-value="value"
    :options="options"
    @change="onEditorChange">
  </Cm5EditorCore>
</template>
<script>
  import Cm5EditorCore from '@/components/Cm5EditorCore.vue'

  export default {
    name: 'CodeMirror',
    components: {
      Cm5EditorCore
    },
    props: {
      value: {
        type: String,
        default: ''
      },
      mode: {
        type: String,
        default: 'text/x-csrc'
      }
    },
    data () {
      return {
        options: {
          mode: 'text/x-csrc',
          lineNumbers: true,
          lineWrapping: false,
          theme: 'solarized',
          tabSize: 4,
          line: true,
          foldGutter: true,
          gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
          autofocus: true
        }
      }
    },
    computed: {
      editor () {
        const core = this.$refs.editorCore
        return core ? core.editor : null
      }
    },
    mounted () {
      this.setOption('mode', this.mode)
    },
    watch: {
      value (newVal) {
        if (newVal !== this.getDocument()) {
          this.setDocument(newVal, { silent: true })
        }
      },
      mode (newVal) {
        this.setOption('mode', newVal)
      }
    },
    methods: {
      getCore () {
        return this.$refs.editorCore || null
      },
      getDocument () {
        const core = this.getCore()
        return core && typeof core.getDocument === 'function' ? core.getDocument() : ''
      },
      setDocument (text, config = {}) {
        const core = this.getCore()
        if (core && typeof core.setDocument === 'function') {
          core.setDocument(text, config)
        }
      },
      setOption (name, value) {
        const core = this.getCore()
        if (core && typeof core.setOption === 'function') {
          core.setOption(name, value)
        }
      },
      onEditorChange (newVal) {
        this.$emit('change', newVal)
        this.$emit('input', newVal)
        this.$emit('update:modelValue', newVal)
      }
    }
  }
</script>

<style scoped>
  .cm5-editor-core {
    height: 500px;
  }
</style>
