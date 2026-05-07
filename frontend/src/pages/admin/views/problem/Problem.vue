<template>
  <div class="problem">
    <Panel :title="title">
      <el-form ref="form" :model="problem" :rules="rules" label-position="top" label-width="70px" size="large">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="_id" :label="$t('m.Display_ID')" :required="true">
              <el-input :placeholder="$t('m.Display_ID')" v-model="problem._id"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item prop="title" :label="$t('m.Title')" required>
              <el-input :placeholder="$t('m.Title')" v-model="problem.title"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="当前课程内容包" required>
              <el-select
                v-model="selectedLanguagePackId"
                class="language-pack-select"
                :disabled="languagePackLoading || languagePackOptions.length === 0">
                <el-option
                  v-for="pack in languagePackOptions"
                  :key="pack.id"
                  :label="pack.name + ' v' + pack.version"
                  :value="String(pack.id)">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item class="full-width-editor-item" prop="description" :label="$t('m.Description')" required>
              <Simditor v-model="problem.description"></Simditor>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item class="full-width-editor-item" prop="input_description" :label="$t('m.Input_Description')" required>
              <Simditor v-model="problem.input_description"></Simditor>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item class="full-width-editor-item" prop="output_description" :label="$t('m.Output_Description')" required>
              <Simditor v-model="problem.output_description"></Simditor>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('m.Time_Limit') + ' (ms)'" required>
              <el-input type="Number" :placeholder="$t('m.Time_Limit')" v-model="problem.time_limit"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('m.Memory_limit') + ' (MB)'" required>
              <el-input type="Number" :placeholder="$t('m.Memory_limit')" v-model="problem.memory_limit"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('m.Difficulty')">
              <el-select class="difficulty-select" size="small" :placeholder="$t('m.Difficulty')" v-model="problem.difficulty">
                <el-option :label="$t('m.Low')" value="Low"></el-option>
                <el-option :label="$t('m.Mid')" value="Mid"></el-option>
                <el-option :label="$t('m.High')" value="High"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="4">
            <el-form-item :label="$t('m.Visible')">
              <el-switch v-model="problem.visible" active-text="" inactive-text=""></el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('m.Tag')" :error="error.tags" required>
              <el-select
                v-model="selectedKcTags"
                multiple
                filterable
                remote
                clearable
                reserve-keyword
                collapse-tags
                placeholder="选择已有知识点标签"
                :remote-method="searchKcTags"
                :loading="kcTagLoading"
                @change="syncSelectedKcTags">
                <el-option
                  v-for="name in kcTagOptions"
                  :key="'kc-tag-' + name"
                  :label="name"
                  :value="name">
                </el-option>
              </el-select>
              <span class="tags">
                <el-tag
                  v-for="tag in displayTags"
                  :closable="true"
                  :close-transition="false"
                  :key="tag"
                  type="success"
                  @close="closeTag(tag)">
                  {{tag}}
                </el-tag>
              </span>
              <el-autocomplete
                v-if="inputVisible"
                size="small"
                class="input-new-tag"
                popper-class="problem-tag-poper"
                v-model="tagInput"
                :trigger-on-focus="false"
                @keyup.enter="addTag"
                @select="addTag"
                :fetch-suggestions="querySearch">
              </el-autocomplete>
              <el-button class="button-new-tag" v-else size="small" @click="inputVisible = true">
                + {{$t('m.New_Tag')}}
              </el-button>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.Languages')" :error="error.languages" required>
              <el-checkbox-group v-model="problem.languages">
                <el-tooltip
                  v-for="lang in allLanguage.languages"
                  :key="'lang'+lang.name"
                  effect="dark"
                  :content="lang.description"
                  placement="top-start">
                  <el-checkbox :value="lang.name" :label="lang.name"></el-checkbox>
                </el-tooltip>
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="sample-section">
          <el-form-item class="sample-item" v-for="(sample, index) in problem.samples" :key="'sample'+index">
            <Accordion :title="'样例 ' + (index + 1)" class="full-width-accordion">
              <template #header><el-button type="warning" size="small" icon="el-icon-delete" @click="deleteSample(index)">
                删除
              </el-button></template>
              <el-row :gutter="20">
                <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12">
                  <el-form-item :label="$t('m.Input_Samples')" required>
                    <el-input :rows="5" type="textarea" :placeholder="$t('m.Input_Samples')" v-model="sample.input"></el-input>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12">
                  <el-form-item :label="$t('m.Output_Samples')" required>
                    <el-input :rows="5" type="textarea" :placeholder="$t('m.Output_Samples')" v-model="sample.output"></el-input>
                  </el-form-item>
                </el-col>
              </el-row>
            </Accordion>
          </el-form-item>
        </div>
        <div class="add-sample-btn">
          <button type="button" class="add-samples" @click="addSample()">
            <i class="el-icon-plus"></i>{{$t('m.Add_Sample')}}
          </button>
        </div>

        <el-form-item class="full-width-editor-item" style="margin-top: 20px" :label="$t('m.Hint')">
          <Simditor v-model="problem.hint" placeholder=""></Simditor>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="参考解语言">
              <el-select
                v-model="problem.reference_solution_language"
                filterable
                clearable
                placeholder="请选择参考解语言">
                <el-option
                  v-for="lang in allLanguage.languages"
                  :key="'reference-solution-lang-' + lang.name"
                  :label="lang.name"
                  :value="lang.name">
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item class="full-width-editor-item" label="参考解代码">
          <code-mirror v-model="problem.reference_solution_code" :mode="referenceSolutionMode"></code-mirror>
        </el-form-item>
        <el-form-item class="test-cases-item" label="测试数据" :error="error.testCase">
          <div v-if="testCasesLoading" class="tc-loading">
            <i class="el-icon-loading"></i> 正在加载测试数据...
          </div>
          <div v-else class="test-case-section">
            <div v-for="(tc, index) in inlineCases" :key="'tc'+index">
              <Accordion :title="'测试点 ' + (index + 1)" class="full-width-accordion">
                <template #header><el-button type="danger" size="small" icon="el-icon-delete" @click="deleteTestCase(index)">
                  删除
                </el-button></template>
                <el-row :gutter="20">
                  <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12">
                    <el-form-item label="输入" required>
                      <el-input
                        :rows="6"
                        type="textarea"
                        placeholder="测试点输入"
                        v-model="tc.input">
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :xs="24" :sm="24" :md="24" :lg="12" :xl="12">
                    <el-form-item label="期望输出" required>
                      <el-input
                        :rows="6"
                        type="textarea"
                        placeholder="测试点期望输出"
                        v-model="tc.output">
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
              </Accordion>
            </div>
            <div class="add-sample-btn">
              <button type="button" class="add-samples" @click="addTestCase()">
                <i class="el-icon-plus"></i> 添加测试点
              </button>
            </div>
          </div>
        </el-form-item>

        <el-form-item :label="$t('m.Source')">
          <el-input :placeholder="$t('m.Source')" v-model="problem.source"></el-input>
        </el-form-item>

        <save @click="submit()">保存</save>
      </el-form>
    </Panel>
  </div>
</template>

<script>
  import Simditor from '../../components/Simditor'
  import Accordion from '../../components/Accordion'
  import CodeMirror from '../../components/CodeMirror'
  import api from '../../api'
  import {
    appendLanguagePackQuery,
    resolveCurrentLanguagePackId
  } from '@admin/utils/languagePackContext'

  const DEFAULT_PROBLEM = () => ({
    _id: '',
    title: '',
    description: '',
    input_description: '',
    output_description: '',
    time_limit: 1000,
    memory_limit: 256,
    difficulty: 'Low',
    visible: true,
    tags: [],
    languages: [],
    template: {},
    samples: [{input: '', output: ''}],
    test_case_id: '',
    test_case_score: [],
    hint: '',
    source: '',
    reference_solution_language: '',
    reference_solution_code: ''
  })
  const DEFAULT_LANGUAGE_NAMES = ['Python3', 'C', 'C++', 'Java']
  const LANGUAGE_MODE_MAP = {
    C: 'text/x-csrc',
    'C++': 'text/x-c++src',
    Java: 'text/x-java',
    Python3: 'text/x-python',
    Python: 'text/x-python',
    JavaScript: 'text/javascript',
    Go: 'text/x-go'
  }

  export default {
    name: 'Problem',
    components: {
      Simditor,
      Accordion,
      CodeMirror
    },
    data () {
      return {
        rules: {
          _id: {required: true, message: '展示 ID 不能为空', trigger: 'blur'},
          title: {required: true, message: '标题不能为空', trigger: 'blur'},
          input_description: {required: true, message: '输入描述不能为空', trigger: 'blur'},
          output_description: {required: true, message: '输出描述不能为空', trigger: 'blur'}
        },
        mode: '',
        problem: {languages: []},
        allLanguage: {languages: []},
        inputVisible: false,
        tagInput: '',
        kcTagOptions: [],
        kcTagLoading: false,
        selectedKcTags: [],
        title: '',
        inlineCases: [{input: '', output: ''}],
        testCasesLoading: false,
        error: {
          tags: '',
          languages: '',
          testCase: ''
        },
        languagePackOptions: [],
        selectedLanguagePackId: '',
        languagePackLoading: false
      }
    },
    mounted () {
      this.initializePage()
    },
    watch: {
      '$route' (to) {
        this.initializePage()
      }
    },
    computed: {
      displayTags () {
        return (this.problem.tags || []).filter(tag => tag !== 'type::coding')
      },
      referenceSolutionMode () {
        const lang = String(this.problem.reference_solution_language || '').trim()
        return LANGUAGE_MODE_MAP[lang] || 'text/x-python'
      }
    },
    methods: {
      async initializePage () {
        if (this.$refs.form && typeof this.$refs.form.resetFields === 'function') {
          this.$refs.form.resetFields()
        }
        this.inlineCases = [{ input: '', output: '' }]
        this.selectedKcTags = []
        this.mode = this.$route.name === 'edit-problem' ? 'edit' : 'add'
        this.title = this.mode === 'edit' ? this.$t('m.Edit_Problem') : this.$t('m.Add_Problem')

        await this.loadLanguagePackOptions()
        await this.loadLanguages()
        this.problem = DEFAULT_PROBLEM()
        if (this.mode === 'edit') {
          await this.fetchProblem()
        } else {
          this.problem.languages = this.allLanguage.languages.map(item => item.name)
        }
        this.loadKCTagOptions()
      },
      loadLanguagePackOptions () {
        this.languagePackLoading = true
        return api.getVisibleLanguagePacks().then(res => {
          this.languagePackOptions = Array.isArray(res.data.data) ? res.data.data : []
          this.selectedLanguagePackId = resolveCurrentLanguagePackId(this.$route.query.language_pack_id, this.languagePackOptions)
          this.syncLanguagePackRoute(true)
          if (!this.selectedLanguagePackId) {
            this.$error('当前没有可用课程内容包，无法新建题目')
          }
        }).catch(() => {
          this.languagePackOptions = []
          this.selectedLanguagePackId = ''
          this.$error('课程内容包加载失败')
        }).finally(() => {
          this.languagePackLoading = false
        })
      },
      loadLanguages () {
        return api.getLanguages().then(res => {
          this.allLanguage = {
            languages: this.normalizeLanguageOptions(res.data && res.data.data && res.data.data.languages)
          }
        }).catch(() => {
          this.allLanguage = {
            languages: this.normalizeLanguageOptions(DEFAULT_LANGUAGE_NAMES)
          }
        })
      },
      syncLanguagePackRoute (replace = false) {
        const query = appendLanguagePackQuery({}, this.selectedLanguagePackId)
        const currentQuery = appendLanguagePackQuery({}, this.$route.query.language_pack_id)
        if (JSON.stringify(query) === JSON.stringify(currentQuery)) {
          return
        }
        const payload = { name: this.$route.name, params: this.$route.params, query }
        if (replace) {
          this.$router.replace(payload)
          return
        }
        this.$router.push(payload)
      },
      normalizeLanguageOptions (rawLanguages) {
        const list = Array.isArray(rawLanguages) ? rawLanguages : DEFAULT_LANGUAGE_NAMES
        return list.map(item => {
          if (item && typeof item === 'object') {
            const name = item.name ? String(item.name) : ''
            if (!name) return null
            const config = item.config && typeof item.config === 'object' ? item.config : {template: ''}
            return {
              name,
              description: item.description || '',
              content_type: item.content_type || LANGUAGE_MODE_MAP[name] || 'text/plain',
              config: Object.assign({template: ''}, config)
            }
          }
          const name = String(item || '').trim()
          if (!name) return null
          return {
            name,
            description: '',
            content_type: LANGUAGE_MODE_MAP[name] || 'text/plain',
            config: {template: ''}
          }
        }).filter(Boolean)
      },
      loadKCTagOptions (keyword = '') {
        if (!this.selectedLanguagePackId) {
          this.kcTagOptions = []
          return Promise.resolve()
        }
        this.kcTagLoading = true
        return api.getKCList({
          keyword,
          page: 1,
          page_size: 100,
          language_pack_id: this.selectedLanguagePackId
        }).then(res => {
          const rawResults = res && res.data && res.data.data ? res.data.data.results : []
          const options = (Array.isArray(rawResults) ? rawResults : [])
            .map(item => item.name)
            .filter(name => name && name !== 'type::coding')
          this.kcTagOptions = Array.from(new Set(options))
          this.refreshSelectedKcTags()
        }).finally(() => {
          this.kcTagLoading = false
        })
      },
      searchKcTags (keyword) {
        this.loadKCTagOptions(keyword)
      },
      refreshSelectedKcTags () {
        const tagList = Array.isArray(this.problem.tags) ? this.problem.tags : []
        const kcSet = new Set(this.kcTagOptions.map(name => String(name).toLowerCase()))
        const selected = tagList.filter(tag => {
          const normalizedTag = String(tag || '').trim().toLowerCase()
          return normalizedTag && normalizedTag !== 'type::coding' && kcSet.has(normalizedTag)
        })
        this.selectedKcTags = Array.from(new Set(selected))
      },
      syncSelectedKcTags () {
        const allTags = Array.isArray(this.problem.tags) ? this.problem.tags.slice() : []
        const kcSet = new Set(this.kcTagOptions.map(name => String(name).toLowerCase()))
        const fixedTags = allTags.filter(tag => String(tag || '').trim().toLowerCase() === 'type::coding')
        const customTags = allTags.filter(tag => {
          const normalizedTag = String(tag || '').trim().toLowerCase()
          return normalizedTag && normalizedTag !== 'type::coding' && !kcSet.has(normalizedTag)
        })
        const merged = [...fixedTags, ...customTags, ...this.selectedKcTags]
        this.problem.tags = Array.from(new Set(merged.map(tag => String(tag || '').trim()).filter(Boolean)))
      },
      fetchProblem () {
        return api.getProblem(this.$route.params.problemId).then(problemRes => {
          let data = problemRes.data.data
          const problemLanguagePackId = data && data.language_pack_id ? String(data.language_pack_id) : ''
          if (problemLanguagePackId) {
            this.selectedLanguagePackId = problemLanguagePackId
            this.syncLanguagePackRoute(true)
          }
          this.problem = Object.assign({}, data, {
            tags: Array.isArray(data.tags) ? data.tags : [],
            languages: Array.isArray(data.languages)
              ? data.languages.map(item => {
                if (typeof item === 'string') return item
                return item && item.name ? String(item.name) : ''
              }).filter(Boolean)
              : [],
            template: data.template && typeof data.template === 'object' ? data.template : {},
            language_pack_id: this.selectedLanguagePackId,
            reference_solution_language: data.reference_solution_language || '',
            reference_solution_code: data.reference_solution_code || ''
          })
          this.autoMatchKCTags()
          this.refreshSelectedKcTags()
          this.testCasesLoading = true
          api.getInlineTestCases(this.$route.params.problemId).then(tcRes => {
            let cases = tcRes.data.data.cases
            this.inlineCases = cases.length > 0 ? cases : [{input: '', output: ''}]
          }).finally(() => {
            this.testCasesLoading = false
          })
        })
      },
      autoMatchKCTags () {
        if (!this.selectedLanguagePackId) {
          return
        }
        api.getKCList({
          page: 1,
          page_size: 100,
          language_pack_id: this.selectedLanguagePackId
        }).then(res => {
          const rawResults = res && res.data && res.data.data ? res.data.data.results : []
          const kcNames = (Array.isArray(rawResults) ? rawResults : []).map(item => item.name).filter(Boolean)
          if (!kcNames.length) return

          const currentTags = Array.isArray(this.problem.tags) ? this.problem.tags.slice() : []
          const currentSet = new Set(currentTags.map(tag => String(tag).toLowerCase()))
          const textForMatch = [this.problem.title, this.problem.description]
            .filter(Boolean)
            .join(' ')
            .toLowerCase()

          const autoMatched = kcNames.filter(name => textForMatch.includes(String(name).toLowerCase())).slice(0, 3)
          const merged = currentTags.slice()
          autoMatched.forEach(name => {
            const key = String(name).toLowerCase()
            if (!currentSet.has(key)) {
              merged.push(name)
              currentSet.add(key)
            }
          })
          this.problem.tags = merged
          this.refreshSelectedKcTags()
        }).catch(() => {})
      },
      querySearch (queryString, suggestionHandler) {
        this.loadKCTagOptions(queryString).catch(() => {
          this.kcTagOptions = []
        }).finally(() => {
          suggestionHandler(this.kcTagOptions.map(name => ({value: name})))
        })
      },
      addTag () {
        let inputValue = this.tagInput
        if (inputValue && inputValue !== 'type::coding' && !this.problem.tags.includes(inputValue)) {
          this.problem.tags.push(inputValue)
        }
        this.inputVisible = false
        this.tagInput = ''
        this.refreshSelectedKcTags()
      },
      closeTag (tag) {
        this.problem.tags.splice(this.problem.tags.indexOf(tag), 1)
        this.refreshSelectedKcTags()
      },
      addSample () {
        this.problem.samples.push({input: '', output: ''})
      },
      deleteSample (index) {
        this.problem.samples.splice(index, 1)
      },
      addTestCase () {
        this.inlineCases.push({input: '', output: ''})
      },
      deleteTestCase (index) {
        this.inlineCases.splice(index, 1)
      },
      submit () {
        if (!this.selectedLanguagePackId) {
          this.$error('请先选择课程内容包')
          return
        }
        if (!this.problem.samples.length) {
          this.$error('Sample is required')
          return
        }
        for (let sample of this.problem.samples) {
          if (!sample.input || !sample.output) {
            this.$error('Sample input and output is required')
            return
          }
        }
        if (!this.problem.tags.length) {
          this.error.tags = 'Please add at least one tag'
          this.$error(this.error.tags)
          return
        }
        if (!this.problem.languages.length) {
          this.error.languages = 'Please choose at least one language for problem'
          this.$error(this.error.languages)
          return
        }
        const validCases = this.inlineCases.filter(tc => tc.input.trim())
        if (validCases.length === 0) {
          this.error.testCase = 'At least one test case with input is required'
          this.$error(this.error.testCase)
          return
        }

        this.problem.languages = this.problem.languages.sort()
        this.problem.template = {}

        api.uploadInlineTestCases(validCases).then(tcRes => {
          this.problem.test_case_id = tcRes.data.data.id
          this.problem.language_pack_id = this.selectedLanguagePackId
          this.problem.test_case_score = tcRes.data.data.info.map(item => ({
            input_name: item.input_name,
            output_name: item.output_name,
            score: 0
          }))
          let funcName = this.mode === 'edit' ? 'editProblem' : 'createProblem'
          api[funcName](this.problem).then(() => {
            this.$router.push({name: 'problem-list'})
          })
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  .problem {
    --el-component-size: 40px;
    --el-component-size-large: 40px;
    --el-component-size-small: 32px;

    .difficulty-select {
      width: 120px;
    }
    .language-pack-select {
      width: 100%;
    }
    .input-new-tag {
      width: 78px;
    }
    :deep(.input-new-tag .el-input__wrapper) {
      min-height: 28px;
      padding: 0 10px;
    }
    :deep(.input-new-tag .el-input__inner) {
      height: 28px;
      line-height: 28px;
      font-size: 12px;
    }
    .button-new-tag {
      height: 24px;
      line-height: 22px;
      padding-top: 0;
      padding-bottom: 0;
    }
    .tags {
      .el-tag {
        margin-right: 10px;
      }
    }
    .accordion {
      margin-bottom: 10px;
    }
    .add-samples {
      width: 100%;
      background-color: #fff;
      border: 1px dashed #aaa;
      outline: none;
      cursor: pointer;
      color: #666;
      height: 35px;
      font-size: 14px;
      &:hover {
        background-color: #f9fafc;
      }
      i {
        margin-right: 10px;
      }
    }
    .add-sample-btn {
      margin-bottom: 10px;
    }
    .tc-loading {
      color: #999;
      padding: 12px 0;
      font-size: 14px;
    }
    .sample-section,
    .test-case-section {
      width: 100%;
    }
    :deep(.full-width-editor-item .el-form-item__content),
    :deep(.sample-item .el-form-item__content),
    :deep(.test-cases-item .el-form-item__content) {
      width: 100%;
      min-width: 0;
      display: block;
    }
    :deep(.full-width-editor-item .simditor),
    :deep(.full-width-accordion),
    :deep(.sample-section .el-textarea),
    :deep(.sample-section .el-input),
    :deep(.test-case-section .el-textarea),
    :deep(.test-case-section .el-input) {
      width: 100%;
      max-width: 100%;
    }
  }
</style>

<style>
  .problem-tag-poper {
    width: 200px !important;
  }
</style>
