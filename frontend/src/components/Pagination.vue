<template>
  <div class="pagination-wrapper">
    <el-config-provider :locale="paginationLocale">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="normalizedPageSizes"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange">
      </el-pagination>
    </el-config-provider>
  </div>
</template>

<script>
  export default {
    name: 'Pagination',
    props: {
      total: {
        type: Number,
        default: 0
      },
      currentPage: {
        type: Number,
        default: 1
      },
      pageSize: {
        type: Number,
        default: 10
      },
      pageSizes: {
        type: Array,
        default: () => [10, 20, 50, 100]
      }
    },
    emits: ['update:currentPage', 'update:pageSize', 'change'],
    computed: {
      paginationLocale () {
        return {
          el: {
            pagination: {
              total: '总计 {total}',
              pagesize: '',
              goto: '前往',
              pageClassifier: '页'
            }
          }
        }
      },
      normalizedPageSizes () {
        const sizes = Array.isArray(this.pageSizes) ? this.pageSizes : [10, 20, 50, 100]
        return sizes
          .map(item => Number(item))
          .filter(item => Number.isInteger(item) && item > 0)
      }
    },
    methods: {
      handleCurrentChange (page) {
        this.$emit('update:currentPage', page)
        this.$emit('change', { page, pageSize: this.pageSize })
      },
      handleSizeChange (size) {
        this.$emit('update:pageSize', size)
        this.$emit('update:currentPage', 1)
        this.$emit('change', { page: 1, pageSize: size })
      }
    }
  }
</script>

<style scoped lang="less">
  .pagination-wrapper {
    display: flex;
    justify-content: center;
    width: 100%;
    margin-top: 16px;
  }
</style>
