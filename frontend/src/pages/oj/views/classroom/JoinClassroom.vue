<template>
  <div class="join-classroom-container">
    <el-card class="join-card">
      <template #header><p>
        <el-icon :size="24"><User /></el-icon>
        加入班级
      </p></template>
      
      <div class="form-container">
        <el-form ref="joinForm" :model="form" :rules="rules" :label-width="100">
          <el-form-item label="邀请码" prop="code">
            <el-input 
              v-model="form.code" 
              placeholder="请输入8位邀请码"
              size="large"
              @keyup.enter="joinClassroom"/>
          </el-form-item>
          
          <el-form-item>
            <el-button 
              type="primary" 
              size="large" 
              style="width: 100%" 
              :loading="joining"
              @click="joinClassroom">
              加入班级
            </el-button>
          </el-form-item>
        </el-form>
        
        <div class="tips">
          <el-alert type="info" show-icon :closable="false" title="请向教师索要邀请码，输入后即可加入班级" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import api from '@oj/api'
import { User } from '@element-plus/icons-vue'
import { encodeRouteCtx } from '@/utils/urlCipher'

export default {
  name: 'JoinClassroom',
  components: { User },
  data () {
    return {
      form: {
        code: ''
      },
      rules: {
        code: [
          { required: true, message: '请输入邀请码', trigger: 'blur' },
          { len: 8, message: '邀请码为8位', trigger: 'blur' }
        ]
      },
      joining: false
    }
  },
  methods: {
    joinClassroom () {
      this.$refs.joinForm.validate(valid => {
        if (valid) {
          this.joining = true
          api.joinClassroom({ code: this.form.code }).then(res => {
            this.$success('加入成功')
            this.joining = false
            const classroomId = res.data.data.classroom_id
            this.$router.push({ name: 'classroom-detail', query: { ctx: encodeRouteCtx({ id: classroomId }) } })
          }).catch(() => {
            this.joining = false
          })
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.join-classroom-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  
  .join-card {
    width: 500px;
    
    .form-container {
      padding: 20px;
      
      .tips {
        margin-top: 20px;
      }
    }
  }
}
</style>
