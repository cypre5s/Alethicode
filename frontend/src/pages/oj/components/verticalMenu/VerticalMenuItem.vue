<template>
  <li @click.stop="handleClick" :class="{disabled: disabled}">
    <slot></slot>
  </li>
</template>

<script>
  import { inject } from 'vue'

  export default {
    name: 'VerticalMenuItem',
    setup () {
      const onMenuItemClick = inject('onMenuItemClick', () => {})
      return { onMenuItemClick }
    },
    props: {
      route: {
        type: [String, Object]
      },
      disabled: {
        type: Boolean,
        default: false
      }
    },
    methods: {
      handleClick () {
        if (this.route) {
          this.onMenuItemClick(this.route)
        }
      }
    }
  }
</script>

<style scoped lang="less">
  .disabled {
    /*background-color: #ccc;*/
    opacity: 0.6;
    /*cursor: not-allowed;*/
    pointer-events: none;
    color: var(--text-disabled);
    &:hover {
      border-left: none;
      color: var(--text-disabled);
      background: transparent;
    }
  }

  li {
    border-bottom: 1px solid var(--border-color);
    color: var(--text-secondary);
    display: block;
    text-align: left;
    padding: 15px 20px;
    transition: all 0.2s ease;
    cursor: pointer;
    
    &:hover {
      background: var(--bg-base);
      border-left: 3px solid var(--primary-color);
      padding-left: 17px; /* Compensate for border width to keep text aligned or shift it slightly */
      color: var(--primary-color);
    }
    
    & > .el-icon {
      font-size: 16px;
      margin-right: 8px;
    }
    
    &:last-child {
      border-bottom: none;
    }
  }
</style>
