<template>
  <div class="nb-toolbar">
    <div class="nb-filter">
      <span class="nb-filter-label">错误类型</span>
      <el-select
        :model-value="selectedCategory"
        clearable
        placeholder="全部类型"
        style="width: 160px"
        @update:model-value="$emit('update:selectedCategory', $event)"
        @change="$emit('change')"
      >
        <el-option v-for="item in categoryOptions" :key="item.value" :value="item.value" :label="item.label" />
      </el-select>
      <span class="nb-filter-label" style="margin-left: 12px;">编程语言</span>
      <el-select
        :model-value="selectedLanguage"
        clearable
        placeholder="全部语言"
        style="width: 140px"
        @update:model-value="$emit('update:selectedLanguage', $event)"
        @change="$emit('change')"
      >
        <el-option v-for="lang in languageOptions" :key="lang" :value="lang" :label="lang" />
      </el-select>
    </div>
    <div class="nb-stats">
      <span>共 <b>{{ groupCount }}</b> 题</span>
      <span class="nb-stats-sep"></span>
      <span><b>{{ entryCount }}</b> 条记录</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'NotebookFilterToolbar',
  emits: ['update:selectedCategory', 'update:selectedLanguage', 'change'],
  props: {
    categoryOptions: { type: Array, default: () => [] },
    languageOptions: { type: Array, default: () => [] },
    selectedCategory: { type: String, default: '' },
    selectedLanguage: { type: String, default: '' },
    groupCount: { type: Number, default: 0 },
    entryCount: { type: Number, default: 0 }
  }
}
</script>

<style lang="less" scoped>
.nb-toolbar {
  background: #fff; border: 1px solid #e8eaed;
  border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,.05);
  padding: 12px 20px; margin-bottom: 18px;
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 12px;
}
.nb-filter { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.nb-filter-label { font-size: 13px; color: #5f6368; font-weight: 500; }
.nb-stats {
  font-size: 13px; color: #9aa0ab;
  display: flex; align-items: center; gap: 6px;
  b { color: #1a1d2e; }
}
.nb-stats-sep { display: inline-block; width: 1px; height: 12px; background: #e8eaed; }
</style>
