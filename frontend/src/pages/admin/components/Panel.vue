<template>
  <section class="admin-panel panel" :class="{'admin-panel--small': small, 'small': small}">
    <header class="admin-panel__header">
      <div class="admin-panel__title title">
        <template v-if="$slots.title">
          <slot name="title"></slot>
        </template>
        <template v-else>
          {{title}}
        </template>
      </div>

      <div v-if="$slots.header" class="admin-panel__toolbar header_right">
        <slot name="header"></slot>
      </div>
    </header>

    <div class="admin-panel__body body">
      <slot></slot>
    </div>
    <footer v-if="$slots.footer" class="admin-panel__footer">
      <slot name="footer"></slot>
    </footer>
  </section>
</template>

<script>
  export default {
    name: 'Panel',
    props: {
      title: {
        type: String,
        required: false
      },
      small: {
        type: Boolean,
        default: false
      }
    }
  }
</script>
<style scoped lang="less">
  .admin-panel {
    margin-bottom: 0;
    background: var(--admin-panel-bg);
    border: 1px solid var(--admin-panel-border);
    border-radius: var(--admin-panel-radius);
    box-shadow: var(--admin-panel-shadow);
    overflow: hidden;

    &--small {
      max-width: 980px;
    }
  }

  .admin-panel__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    padding: 20px 22px 16px;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(246, 249, 252, 0.92) 100%);
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  }

  .admin-panel__title {
    margin: 0;
    color: var(--admin-text-strong);
    font-size: 20px;
    font-weight: 700;
    line-height: 1.35;
    letter-spacing: -0.01em;
  }

  .admin-panel__toolbar {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: var(--admin-toolbar-gap);
    flex-wrap: wrap;
    margin-left: auto;
  }

  .admin-panel__body {
    padding: 18px 22px 22px;
  }

  .admin-panel__footer {
    padding: 0 22px 22px;
  }
</style>
<style lang="less">
  .panel-options {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    padding-top: 18px;

    > .page {
      margin-left: auto;
    }
  }
</style>
