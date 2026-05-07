<template>
  <div class="profile-setting">
    <div class="st-card">
      <div class="card-head">
        <div class="card-title">
          <div class="card-title-bar"></div>
          头像设置
        </div>
        <span class="card-meta">JPG · PNG · GIF · 最大 2 MB</span>
      </div>
      <div class="card-body">
        <template v-if="avatarOption.imgSrc">
          <div class="cropper-area">
            <div class="cropper-main">
              <vueCropper
                ref="cropper"
                autoCrop
                fixed
                :autoCropWidth="200"
                :autoCropHeight="200"
                :img="avatarOption.imgSrc"
                :outputSize="avatarOption.size"
                :outputType="avatarOption.outputType"
                :info="true"
                @realTime="realTime"
              />
            </div>
            <div class="cropper-actions">
              <div class="cropper-btn-row">
                <button class="cr-btn" @click="rotate('left')" title="左旋" @mousedown="ripple">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
                  </svg>
                </button>
                <button class="cr-btn" @click="rotate('right')" title="右旋" @mousedown="ripple">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.13-9.36L23 10"/>
                  </svg>
                </button>
                <button class="cr-btn" @click="reselect" title="重选" @mousedown="ripple">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="1 4 1 10 7 10"/><polyline points="23 20 23 14 17 14"/>
                    <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"/>
                  </svg>
                </button>
                <button class="cr-btn cr-btn-primary" @click="finishCrop" title="确认裁剪" @mousedown="ripple">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </button>
              </div>
              <div class="cropper-preview-box" v-if="preview.w">
                <div :style="{ width: preview.w + 'px', height: preview.h + 'px', overflow: 'hidden', borderRadius: '50%' }">
                  <div :style="preview.div">
                    <img :src="avatarOption.imgSrc" :style="preview.img">
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div
            class="upload-zone"
            @click="triggerUpload"
            @dragover.prevent="onDragOver"
            @dragleave="onDragLeave"
            @drop.prevent="onDrop"
            @mousedown="ripple"
            :class="{ 'drag-over': isDragOver }"
          >
            <div class="upload-icon-wrap">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="16 16 12 12 8 16"/>
                <line x1="12" y1="12" x2="12" y2="21"/>
                <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"/>
              </svg>
            </div>
            <div class="upload-title">拖拽图片到此处，或点击选择文件</div>
            <div class="upload-sub">建议使用正方形图片，显示效果更佳</div>
            <div class="upload-formats">
              <span class="fmt-tag">JPG</span>
              <span class="fmt-tag">PNG</span>
              <span class="fmt-tag">GIF</span>
              <span class="fmt-tag">最大 2 MB</span>
            </div>
          </div>
        </template>

        <input type="file" ref="fileInput" accept=".jpg,.jpeg,.png,.bmp,.gif" style="display:none" @change="onFileSelect">

        <div class="preview-strip" v-if="previewFile.name">
          <img v-if="previewFile.src" :src="previewFile.src" class="preview-thumb" />
          <div v-else class="preview-thumb-placeholder">{{ avatarLetter }}</div>
          <div class="preview-info">
            <div class="preview-filename">{{ previewFile.name }}</div>
            <div class="preview-filesize">{{ previewFile.sizeLabel }}</div>
          </div>
          <button class="preview-remove" @click="removeFile" @mousedown="ripple">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
            移除
          </button>
        </div>
      </div>
    </div>

    <ElDialog v-model="uploadModalVisible" title="确认上传头像" width="400px">
      <div class="upload-modal-body">
        <p>您的头像将设置为：</p>
        <img v-if="uploadImgSrc" :src="uploadImgSrc" class="upload-modal-img"/>
      </div>
      <template #footer><div>
        <button class="st-btn st-btn-primary" @click="uploadAvatar" :disabled="loadingUploadBtn" @mousedown="ripple">
          <template v-if="loadingUploadBtn">
            <span class="st-spinner"></span> 上传中...
          </template>
          <template v-else>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="16 16 12 12 8 16"/><line x1="12" y1="12" x2="12" y2="21"/>
              <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"/>
            </svg>
            确认上传
          </template>
        </button>
      </div></template>
    </ElDialog>

    <div class="st-card" style="animation-delay:.12s">
      <div class="card-head">
        <div class="card-title">
          <div class="card-title-bar"></div>
          个人信息设置
        </div>
      </div>
      <div class="card-body">
        <div class="section-sep">
          <div class="section-sep-line"></div>
          基本信息
        </div>
        <div class="form-grid">
          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              真实姓名
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
                </svg>
              </span>
              <input class="f-input" type="text" v-model="formProfile.real_name" placeholder="请输入真实姓名" maxlength="20" @input="onNameInput">
              <span class="field-counter" :class="{ warn: nameCount > 17 }">{{ nameCount }}/20</span>
              <div class="field-underline"></div>
            </div>
          </div>

          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              个性签名
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
              </span>
              <input class="f-input" type="text" v-model="formProfile.mood" placeholder="一句话介绍自己" maxlength="50" @input="onMoodInput">
              <span class="field-counter" :class="{ warn: moodCount > 42 }">{{ moodCount }}/50</span>
              <div class="field-underline"></div>
            </div>
          </div>

          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
              学校
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>
                </svg>
              </span>
              <input class="f-input" type="text" v-model="formProfile.school" placeholder="所在学校">
              <div class="field-underline"></div>
            </div>
          </div>

          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
              </svg>
              专业
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
                </svg>
              </span>
              <input class="f-input" type="text" v-model="formProfile.major" placeholder="所学专业">
              <div class="field-underline"></div>
            </div>
          </div>

        </div>

        <div class="section-sep">
          <div class="section-sep-line"></div>
          社交主页
        </div>
        <div class="form-grid">
          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
              </svg>
              个人博客
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                  <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                </svg>
              </span>
              <input class="f-input" type="url" v-model="formProfile.blog" placeholder="https://your-blog.com">
              <div class="field-underline"></div>
            </div>
          </div>

          <div class="field">
            <label class="field-label">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/>
              </svg>
              GitHub
            </label>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"/>
                </svg>
              </span>
              <input class="f-input" type="text" v-model="formProfile.github" placeholder="github.com/username">
              <div class="field-underline"></div>
            </div>
          </div>
        </div>

        <div class="form-footer">
          <span class="footer-hint">
            <span class="hint-dot"></span>
            修改后需保存才能生效
          </span>
          <div class="footer-btns">
            <button class="st-btn st-btn-ghost" @click="resetForm" @mousedown="ripple">重置</button>
            <button class="st-btn st-btn-primary" :class="{ 'is-saving': saveState === 'saving', 'is-saved': saveState === 'saved' }" @click="saveAll" @mousedown="ripple">
              <span class="save-inner">
                <template v-if="saveState === 'saving'">
                  <span class="st-spinner"></span> 保存中...
                </template>
                <template v-else-if="saveState === 'saved'">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  已保存
                </template>
                <template v-else>
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                    <polyline points="17 21 17 13 7 13 7 21"/>
                    <polyline points="7 3 7 8 15 8"/>
                  </svg>
                  保存全部
                </template>
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import utils from '@/utils/utils'
import { VueCropper } from 'vue-cropper'
import 'vue-cropper/dist/index.css'
import { types } from '@/store'

export default {
  name: 'ProfileSetting',
  components: { VueCropper },
  data () {
    return {
      saveState: 'idle',
      loadingUploadBtn: false,
      uploadModalVisible: false,
      uploadImgSrc: '',
      isDragOver: false,
      preview: {},
      previewFile: {
        name: '',
        sizeLabel: '',
        src: ''
      },
      avatarOption: {
        imgSrc: '',
        size: 0.8,
        outputType: 'png'
      },
      formProfile: {
        real_name: '',
        mood: '',
        major: '',
        blog: '',
        school: '',
        github: ''
      }
    }
  },
  computed: {
    nameCount () {
      return (this.formProfile.real_name || '').length
    },
    moodCount () {
      return (this.formProfile.mood || '').length
    },
    avatarLetter () {
      const profile = this.$store.state.user.profile
      const u = profile.user
      if (u && u.username) return u.username.charAt(0).toUpperCase()
      return '?'
    }
  },
  mounted () {
    let profile = this.$store.state.user.profile
    Object.keys(this.formProfile).forEach(element => {
      if (profile[element] !== undefined && profile[element] !== null) {
        this.formProfile[element] = profile[element]
      }
    })
  },
  methods: {
    ripple (e) {
      const el = e.currentTarget
      const r = el.getBoundingClientRect()
      const size = Math.max(r.width, r.height) * 2
      const rp = document.createElement('span')
      rp.className = 'st-ripple'
      Object.assign(rp.style, {
        width: size + 'px',
        height: size + 'px',
        left: (e.clientX - r.left - size / 2) + 'px',
        top: (e.clientY - r.top - size / 2) + 'px'
      })
      el.appendChild(rp)
      rp.addEventListener('animationend', () => rp.remove())
    },
    onNameInput () {},
    onMoodInput () {},

    triggerUpload () {
      this.$refs.fileInput.click()
    },
    checkFileType (file) {
      if (!/\.(gif|jpg|jpeg|png|bmp|GIF|JPG|PNG)$/.test(file.name)) {
        this.$settingsToast('不支持的文件格式，请选择图片文件', 'warn')
        return false
      }
      return true
    },
    checkFileSize (file) {
      if (file.size > 2 * 1024 * 1024) {
        this.$settingsToast('文件大小超过 2 MB，请重新选择', 'warn')
        return false
      }
      return true
    },
    onFileSelect (e) {
      const file = e.target.files[0]
      if (!file) return
      this.handleFile(file)
    },
    handleFile (file) {
      if (!this.checkFileType(file) || !this.checkFileSize(file)) return
      const reader = new window.FileReader()
      reader.onload = (e) => {
        this.avatarOption.imgSrc = e.target.result
        this.previewFile = {
          name: file.name,
          sizeLabel: (file.size / 1024).toFixed(1) + ' KB · ' + (file.type.split('/')[1] || 'image').toUpperCase(),
          src: e.target.result
        }
      }
      reader.readAsDataURL(file)
    },
    removeFile () {
      this.$refs.fileInput.value = ''
      this.previewFile = { name: '', sizeLabel: '', src: '' }
      this.avatarOption.imgSrc = ''
      this.$settingsToast('已移除所选图片', 'warn')
    },
    onDragOver () {
      this.isDragOver = true
    },
    onDragLeave () {
      this.isDragOver = false
    },
    onDrop (e) {
      this.isDragOver = false
      const file = e.dataTransfer.files[0]
      if (file && file.type.startsWith('image/')) {
        this.handleFile(file)
      } else {
        this.$settingsToast('请上传图片文件', 'warn')
      }
    },

    realTime (data) {
      this.preview = data
    },
    rotate (direction) {
      if (direction === 'left') {
        this.$refs.cropper.rotateLeft()
      } else {
        this.$refs.cropper.rotateRight()
      }
    },
    reselect () {
      this.avatarOption.imgSrc = ''
      this.previewFile = { name: '', sizeLabel: '', src: '' }
      this.$settingsToast('已重置图片选择', 'warn')
    },
    finishCrop () {
      this.$refs.cropper.getCropData(data => {
        this.uploadImgSrc = data
        this.uploadModalVisible = true
      })
    },
    uploadAvatar () {
      this.$refs.cropper.getCropBlob(blob => {
        let form = new window.FormData()
        let file = new window.File([blob], 'avatar.' + this.avatarOption.outputType)
        form.append('image', file)
        this.loadingUploadBtn = true
        api.uploadAvatar(form).then(() => {
          this.loadingUploadBtn = false
          this.$settingsToast('头像设置成功')
          this.uploadModalVisible = false
          this.avatarOption.imgSrc = ''
          this.previewFile = { name: '', sizeLabel: '', src: '' }
          this.$store.dispatch('getProfile')
        }, () => {
          this.loadingUploadBtn = false
          this.$settingsToast('头像上传失败，请重试', 'warn')
        })
      })
    },

    saveAll () {
      if (this.saveState !== 'idle') return
      this.saveState = 'saving'
      let updateData = utils.filterEmptyValue(Object.assign({}, this.formProfile))
      api.updateProfile(updateData).then(res => {
        this.$store.commit(types.CHANGE_PROFILE, { profile: res.data.data })
        this.saveState = 'saved'
        this.$settingsToast('个人信息已成功保存')
        setTimeout(() => {
          this.saveState = 'idle'
        }, 2200)
      }, _ => {
        this.saveState = 'idle'
        this.$settingsToast('保存失败，请重试', 'warn')
      })
    },
    resetForm () {
      Object.keys(this.formProfile).forEach(k => {
        this.formProfile[k] = ''
      })
      this.$settingsToast('已重置所有字段', 'warn')
    }
  }
}
</script>

<style lang="less" scoped>
.profile-setting {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 卡片 */
.st-card {
  background: var(--st-bg-card);
  border: 1px solid var(--st-border);
  border-radius: var(--st-radius);
  box-shadow: var(--st-shadow-sm);
  overflow: hidden;
  animation: ps-fadeUp .4s ease both;
}
.st-card:nth-child(2) { animation-delay: .12s; }

@keyframes ps-fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: none; }
}

.card-head {
  padding: 18px 22px 0;
  display: flex; align-items: center; justify-content: space-between;
}
.card-title {
  font-size: 14px; font-weight: 600; color: var(--st-text);
  display: flex; align-items: center; gap: 10px;
}
.card-title-bar {
  width: 3px; height: 16px; border-radius: 2px; background: var(--st-blue);
}
.card-meta { font-size: 11px; color: var(--st-text-3); }
.card-body { padding: 18px 22px 22px; }

/* 上传区域 */
.upload-zone {
  border: 2px dashed var(--st-blue-b);
  border-radius: var(--st-radius);
  background: var(--st-blue-l);
  padding: 28px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  cursor: pointer; transition: all .2s;
  position: relative; user-select: none; overflow: hidden;
}
.upload-zone:hover {
  border-color: var(--st-blue);
  background: #e8f0fe;
  transform: translateY(-1px);
  box-shadow: var(--st-shadow-md);
}
.upload-zone.drag-over {
  border-color: var(--st-blue);
  background: #e8f0fe;
  transform: scale(1.01);
}

.upload-icon-wrap {
  width: 48px; height: 48px; border-radius: 50%;
  background: #fff; border: 1.5px solid var(--st-blue-b);
  display: flex; align-items: center; justify-content: center;
  transition: all .2s; box-shadow: 0 2px 8px rgba(26,115,232,.1);
  color: var(--st-blue);
}
.upload-zone:hover .upload-icon-wrap {
  border-color: var(--st-blue);
  background: var(--st-blue);
  color: #fff;
  transform: scale(1.08);
}
.upload-title { font-size: 13px; font-weight: 500; color: var(--st-text); }
.upload-sub { font-size: 11px; color: var(--st-text-3); }

.upload-formats { display: flex; gap: 6px; margin-top: 2px; }
.fmt-tag {
  font-size: 10px; padding: 2px 7px; border-radius: 5px;
  background: #fff; border: 1px solid var(--st-blue-b);
  color: var(--st-blue); font-weight: 500;
}

/* 预览条 */
.preview-strip {
  display: flex; align-items: center; gap: 14px;
  padding: 12px 16px; margin-top: 12px;
  background: #f6fef9; border: 1px solid #bbf7d0;
  border-radius: var(--st-radius-s);
}
.preview-thumb {
  width: 44px; height: 44px; border-radius: 50%;
  object-fit: cover; border: 2px solid var(--st-green); flex-shrink: 0;
}
.preview-thumb-placeholder {
  width: 44px; height: 44px; border-radius: 50%;
  background: linear-gradient(135deg, #4f7cff, #a78bfa);
  border: 2px solid var(--st-green); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 16px; font-weight: 700;
}
.preview-info { flex: 1; min-width: 0; }
.preview-filename {
  font-size: 12px; font-weight: 500; color: var(--st-text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.preview-filesize { font-size: 11px; color: var(--st-text-3); margin-top: 2px; }
.preview-remove {
  font-size: 11px; font-weight: 500; padding: 4px 12px; border-radius: 6px;
  border: 1px solid #fecaca; color: var(--st-red); background: #fff5f5;
  cursor: pointer; transition: all .15s; flex-shrink: 0;
  display: flex; align-items: center; gap: 5px;
  position: relative; overflow: hidden;
}
.preview-remove:hover { background: var(--st-red); color: #fff; border-color: var(--st-red); }

/* 裁剪区域 */
.cropper-area {
  display: flex; gap: 16px; flex-wrap: wrap;
}
.cropper-main {
  width: 400px; height: 300px; flex-shrink: 0;
}
.cropper-actions {
  display: flex; flex-direction: column; gap: 12px;
}
.cropper-btn-row { display: flex; gap: 6px; }
.cr-btn {
  width: 36px; height: 36px; border-radius: var(--st-radius-s);
  border: 1px solid var(--st-border); background: #fff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all .15s; color: var(--st-text-2);
  position: relative; overflow: hidden;
}
.cr-btn:hover { background: #f5f6fa; border-color: #c8ccd4; }
.cr-btn-primary {
  background: var(--st-blue); border-color: var(--st-blue); color: #fff;
}
.cr-btn-primary:hover { background: #1558d6; }
.cropper-preview-box {
  border: 1px solid var(--st-border); border-radius: 50%; overflow: hidden;
  width: fit-content; box-shadow: var(--st-shadow-sm);
}

/* 上传弹窗 */
.upload-modal-body {
  text-align: center;
  p { font-size: 14px; margin-bottom: 12px; color: var(--st-text-2); }
}
.upload-modal-img {
  max-width: 200px; border-radius: 50%;
  box-shadow: 0 0 0 3px #fff, 0 0 0 5px #e8f0fe;
}

/* 表单分区 */
.section-sep {
  font-size: 11px; font-weight: 600; color: var(--st-text-3);
  letter-spacing: .6px; text-transform: uppercase;
  padding-bottom: 14px; border-bottom: 1px solid var(--st-border-s);
  margin-bottom: 20px;
  display: flex; align-items: center; gap: 8px;
}
.section-sep-line {
  width: 3px; height: 12px; border-radius: 2px; background: var(--st-blue);
}

.form-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 20px 24px;
  margin-bottom: 24px;
}

.field { display: flex; flex-direction: column; }
.field-label {
  font-size: 11px; font-weight: 500; color: var(--st-text-2);
  margin-bottom: 6px; letter-spacing: .2px;
  display: flex; align-items: center; gap: 5px;
}
.field-label svg { color: var(--st-text-3); }
.field-wrap { position: relative; }

.f-input, .f-select {
  width: 100%; height: 38px;
  padding: 0 36px 0 36px;
  font-size: 13px; color: var(--st-text); font-family: inherit;
  background: #fff; border: 1px solid var(--st-border);
  border-radius: var(--st-radius-s); outline: none; transition: all .2s;
}
.f-input:hover, .f-select:hover { border-color: #c5d4fb; }
.f-input:focus, .f-select:focus {
  border-color: var(--st-blue);
  box-shadow: 0 0 0 3px rgba(26,115,232,.1);
}
.f-input::placeholder { color: var(--st-text-3); }

.field-underline {
  position: absolute; bottom: 0; left: 0; right: 0;
  height: 2px; background: var(--st-blue);
  border-radius: 0 0 var(--st-radius-s) var(--st-radius-s);
  transform: scaleX(0); transform-origin: left center;
  transition: transform .25s cubic-bezier(.4,0,.2,1);
  pointer-events: none;
}
.field-wrap:focus-within .field-underline { transform: scaleX(1); }

.field-icon {
  position: absolute; left: 11px; top: 50%; transform: translateY(-50%);
  color: var(--st-text-3); pointer-events: none; transition: color .2s;
  display: flex; align-items: center;
}
.field-wrap:focus-within .field-icon { color: var(--st-blue); }

.field-counter {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  font-size: 10px; color: var(--st-text-3); pointer-events: none;
  font-variant-numeric: tabular-nums;
}
.field-counter.warn { color: var(--st-red); }

.f-select {
  padding-left: 36px; padding-right: 28px;
  appearance: none; cursor: pointer;
}
.select-caret {
  position: absolute; right: 10px; top: 50%; transform: translateY(-50%);
  pointer-events: none; color: var(--st-text-3);
}

/* 表单底部 */
.form-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: 20px; border-top: 1px solid var(--st-border-s); margin-top: 4px;
}
.footer-hint {
  font-size: 11px; color: var(--st-text-3);
  display: flex; align-items: center; gap: 6px;
}
.hint-dot {
  width: 5px; height: 5px; border-radius: 50%; background: var(--st-text-3);
}
.footer-btns { display: flex; gap: 10px; }

/* 按钮 */
.st-btn {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 9px 20px; border-radius: var(--st-radius-s);
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all .18s; border: 1px solid transparent;
  font-family: inherit; user-select: none;
  position: relative; overflow: hidden;
}
.st-btn-ghost {
  background: transparent; color: var(--st-text-2); border-color: var(--st-border);
}
.st-btn-ghost:hover { background: #f5f6fa; border-color: #c8ccd4; color: var(--st-text); }
.st-btn-ghost:active { transform: scale(.98); }
.st-btn-primary {
  background: var(--st-blue); color: #fff; border-color: var(--st-blue);
}
.st-btn-primary:hover {
  background: #1558d6; border-color: #1558d6;
  box-shadow: 0 4px 12px rgba(26,115,232,.28);
}
.st-btn-primary:active { transform: scale(.98); }
.st-btn-primary.is-saving { pointer-events: none; opacity: .75; }
.st-btn-primary.is-saved {
  background: var(--st-green); border-color: var(--st-green);
  box-shadow: 0 4px 12px rgba(52,168,83,.28);
  pointer-events: none;
}

.save-inner {
  display: flex; align-items: center; gap: 7px;
}

.st-spinner {
  display: inline-block;
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,.35);
  border-top-color: #fff;
  animation: st-spin .65s linear infinite;
}
@keyframes st-spin { to { transform: rotate(360deg); } }
</style>
