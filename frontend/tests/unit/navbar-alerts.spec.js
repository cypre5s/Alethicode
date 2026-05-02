/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

jest.mock('@oj/api', () => ({
  __esModule: true,
  default: {
    getClassroomList: jest.fn(),
    getInterventionCandidates: jest.fn()
  }
}))

function loadNavBarComponent() {
  const filePath = path.resolve(__dirname, '../../src/pages/oj/components/NavBar.vue')
  const source = fs.readFileSync(filePath, 'utf8')
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  if (!match) {
    throw new Error('NavBar.vue script block not found')
  }
  const transformed = babel.transformSync(match[1], {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  const localRequire = (request) => {
    if (request === '@oj/api') {
      return require('@oj/api')
    }
    if (request === 'vuex') {
      return {
        mapGetters: () => ({}),
        mapActions: () => ({})
      }
    }
    if (request === '@element-plus/icons-vue') {
      return {}
    }
    if (request.startsWith('@oj/views/user/')) {
      return {}
    }
    return require(request)
  }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, localRequire)
  return module.exports.default || module.exports
}

describe('navbar teacher alerts', () => {
  beforeEach(() => {
    jest.resetModules()
    jest.clearAllMocks()
  })

  test('loadAlerts should normalize paginated classroom responses before slicing', async () => {
    const api = require('@oj/api').default
    api.getClassroomList.mockResolvedValue({
      data: {
        data: {
          results: [
            { id: 101 },
            { id: 102 },
            { id: 103 },
            { id: 104 }
          ]
        }
      }
    })
    api.getInterventionCandidates.mockResolvedValue({
      data: {
        data: {
          candidates: [
            {
              user_id: 'u-1',
              username: '小明',
              reason: '需要关注',
              urgency: 'high',
              problem_title: '两数之和'
            }
          ]
        }
      }
    })

    const component = loadNavBarComponent()
    const vm = {
      teacherAlerts: [],
      unreadAlertCount: 0
    }

    await expect(component.methods.loadAlerts.call(vm)).resolves.toBeUndefined()

    expect(api.getInterventionCandidates).toHaveBeenCalledTimes(3)
    expect(vm.teacherAlerts).toHaveLength(3)
    expect(vm.unreadAlertCount).toBe(3)
    expect(vm.teacherAlerts[0]).toMatchObject({
      student_name: '小明',
      message: '需要关注',
      level: 'high',
      problem_title: '两数之和'
    })
  })
})
