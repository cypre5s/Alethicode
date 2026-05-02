<template>
  <div class="problem-recommendations">
    <div v-if="loading" v-loading="true" style="min-height:200px"></div>
    <div v-else>
      <div v-if="recommendations.length === 0" class="empty-state">
        <el-icon :size="60" color="#9e9e9e"><Box /></el-icon>
        <p>暂无推荐</p>
      </div>
      <div v-else class="recommendations-list">
        <div
          v-for="(rec, index) in recommendations"
          :key="index"
          class="recommendation-item"
        >
          <div class="rec-header">
            <div class="rec-skill">
              <el-icon :size="18" color="#ffc107"><Sunny /></el-icon>
              <span>{{ rec.skill || rec.problem_key || rec.title || '推荐题目' }}</span>
            </div>
            <div class="rec-tags">
              <Tag v-if="rec.adaptive_score !== undefined" color="blue" size="default">
                分值 {{ formatScore(rec.adaptive_score) }}
              </Tag>
              <Tag :color="getDifficultyColor(rec.difficulty)" size="default">
                {{ rec.difficulty || 'Medium' }}
              </Tag>
            </div>
          </div>

          <div class="rec-reason">
            <el-icon :size="16"><ChatSquare /></el-icon>
            <span>{{ rec.reason }}</span>
          </div>

          <div class="rec-actions">
            <el-button type="default" size="small" @click="goProblems(rec)">
              <el-icon><Search /></el-icon>
              查看题目
            </el-button>
          </div>
        </div>

        <!-- 策略说明 -->
        <div class="strategy-info">
          <el-icon :size="16" color="#2196f3"><InfoFilled /></el-icon>
          <span>{{ getStrategyDesc() }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Box, Sunny, ChatSquare, Search, InfoFilled } from '@element-plus/icons-vue'

export default {
  name: 'ProblemRecommendations',
  components: { Box, Sunny, ChatSquare, Search, InfoFilled },
  props: {
    recommendations: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    strategy: {
      type: String,
      default: 'balanced'
    }
  },
  methods: {
    getDifficultyColor (difficulty) {
      const colors = {
        'Low': 'success',
        'Mid': 'warning',
        'High': 'error',
        'Easy': 'success',
        'Medium': 'warning',
        'Hard': 'error'
      }
      return colors[difficulty] || 'default'
    },
    formatScore (v) {
      const n = Number(v)
      if (Number.isNaN(n)) return '-'
      return n.toFixed(3)
    },
    goProblems (rec) {
      if (rec && rec.problem_key) {
        this.$router.push({
          name: 'problem-details',
          params: {
            problemID: rec.problem_key
          }
        })
        return
      }
      this.$router.push({
        name: 'problem-list',
        query: {
          skill: rec ? rec.skill : '',
          difficulty: rec ? rec.difficulty : ''
        }
      })
    },
    getStrategyDesc () {
      const descriptions = {
        'balanced': '平衡策略：综合考虑薄弱、遗忘和保持练习',
        'adaptive': '自适应难度：优先推荐掌握度处于进阶区间（0.3~0.7）的题目',
        'weak': '薄弱策略：专注于掌握度较低的技能',
        'forgotten': '遗忘策略：针对长时间未练习的技能',
        'challenge': '挑战策略：推荐你擅长领域的高难度题目'
      }
      return descriptions[this.strategy] || ''
    }
  }
}
</script>

<style lang="less" scoped>
.problem-recommendations {
  position: relative;
  min-height: 200px;

  .empty-state {
    text-align: center;
    padding: 40px 20px;

    p {
      margin: 10px 0;
      font-size: 16px;
      color: #7f8c8d;
    }
  }

  .recommendations-list {
    .recommendation-item {
      padding: 12px;
      margin-bottom: 10px;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      background: #fafafa;
      transition: all 0.3s;

      &:hover {
        border-color: #2196f3;
        box-shadow: 0 2px 6px rgba(33, 150, 243, 0.1);
      }

      .rec-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;

        .rec-skill {
          display: flex;
          align-items: center;
          font-size: 15px;
          font-weight: 600;
          color: #2c3e50;

          i {
            margin-right: 6px;
          }
        }
        .rec-tags {
          display: flex;
          gap: 6px;
          align-items: center;
        }
      }

      .rec-reason {
        display: flex;
        align-items: flex-start;
        padding: 8px 10px;
        background: #fff;
        border-radius: 4px;
        margin-bottom: 8px;
        font-size: 13px;
        color: #546e7a;
        line-height: 1.5;

        i {
          margin-right: 5px;
          margin-top: 2px;
          flex-shrink: 0;
        }

        span {
          flex: 1;
        }
      }

      .rec-actions {
        text-align: right;
      }
    }

    .strategy-info {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      margin-top: 15px;
      background: #e3f2fd;
      border-radius: 6px;
      font-size: 12px;
      color: #1976d2;

      i {
        margin-right: 5px;
        flex-shrink: 0;
      }
    }
  }
}
</style>
