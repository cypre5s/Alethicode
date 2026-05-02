<template>
  <pre class="submission-highlight" v-highlight="displayCode"><code :class="language"></code></pre>
</template>

<script>
  export default {
    name: 'Highlight',
    props: {
      language: {
        type: String
      },
      code: {
        required: true,
        type: String
      }
    },
    computed: {
      displayCode () {
        // 保持提交视图与 OJ 编辑器代码块风格一致
        return (this.code || '').replace(/^\n+/, '')
      }
    }
  }
</script>

<style scoped lang="less">
  .submission-highlight {
    margin: 0;
    padding: 14px 16px;
    border: 1px solid #e8eaec;
    border-radius: 6px;
    background: #f8fafc;
    overflow-x: auto;

    code {
      display: block;
      white-space: pre;
      font-size: 14px;
      line-height: 1.6;
      font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
    }
  }
</style>
