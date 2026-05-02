<template>
  <div id="app">
    <router-view></router-view>
  </div>
</template>

<script>
  export default {
    name: 'AdminApp',
    components: {}
  }
</script>

<style lang="less">
  body {
    margin: 0;
    padding: 0;
    font-family: var(--admin-font-sans, "PingFang SC", "Microsoft YaHei", Arial, sans-serif);
    font-size: 14px;
    -webkit-font-smoothing: antialiased;
    background: var(--admin-shell-bg, #f3f7fc);
  }

  #app {
    position: relative;
    min-height: 100vh;
    width: 100%;
  }
</style>
