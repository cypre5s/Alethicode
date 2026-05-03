/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('L99 data flow — API → component → render chain', () => {
  // ===== Timeline data flow =====
  describe('Timeline data flow', () => {
    const timeline = readSource('../../src/pages/oj/views/user/twin/LearningTimeline.vue')
    const event = readSource('../../src/pages/oj/views/user/twin/LearningTimelineEvent.vue')
    test('Timeline imports api from @oj/api', () => { expect(timeline).toContain("import api from '@oj/api'") })
    test('Timeline calls getLearningTimeline with from/to/kinds/limit', () => { expect(timeline).toContain('getLearningTimeline'); expect(timeline).toContain('from:'); expect(timeline).toContain('to:'); expect(timeline).toContain('kinds:'); expect(timeline).toContain('limit:') })
    test('Timeline stores events in data', () => { expect(timeline).toContain('events: []') })
    test('Timeline groups events by date', () => { expect(timeline).toContain('groupedEvents') })
    test('Timeline passes event to child', () => { expect(timeline).toContain(':event="event"') })
    test('TimelineEvent receives event prop', () => { expect(event).toContain("event: { type: Object, required: true }") })
    test('TimelineEvent renders dot with color mapping', () => { expect(event).toContain('dotStyle'); expect(event).toContain('KIND_COLORS') })
    test('TimelineEvent emits open event', () => { expect(event).toContain("$emit('open', event)") })
    test('Timeline handles open event', () => { expect(timeline).toContain('@open="handleOpenEvent"') })
    test('Timeline navigates on open', () => { expect(timeline).toContain('$router.push') })
  })

  // ===== KC Galaxy data flow =====
  describe('KC Galaxy data flow', () => {
    const galaxy = readSource('../../src/pages/oj/views/user/twin/KcGalaxyView.vue')
    const drawer = readSource('../../src/pages/oj/views/user/twin/KcDetailDrawer.vue')
    test('Galaxy stores nodes and edges', () => { expect(galaxy).toContain('nodes: []'); expect(galaxy).toContain('edges: []') })
    test('Galaxy maps nodes to ECharts data', () => { expect(galaxy).toContain('graphNodes'); expect(galaxy).toContain('symbolSize') })
    test('Galaxy maps edges to ECharts links', () => { expect(galaxy).toContain('graphEdges'); expect(galaxy).toContain('source:'); expect(galaxy).toContain('target:') })
    test('Galaxy click sets selectedNode', () => { expect(galaxy).toContain('selectedNode'); expect(galaxy).toContain('drawerVisible') })
    test('Drawer receives node+edges props', () => { expect(drawer).toContain("node: { type: Object"); expect(drawer).toContain("edges: { type: Array") })
    test('Drawer computes relatedNodes from edges', () => { expect(drawer).toContain('relatedIds'); expect(drawer).toContain('from_kc_id'); expect(drawer).toContain('to_kc_id') })
    test('Drawer emits focus-node', () => { expect(drawer).toContain("$emit('focus-node'") })
    test('Galaxy handles focusOnNode', () => { expect(galaxy).toContain('focusOnNode') })
  })

  // ===== Persona data flow =====
  describe('Persona data flow', () => {
    const card = readSource('../../src/pages/oj/views/user/twin/TwinPersonaCard.vue')
    test('Persona stores summaryText', () => { expect(card).toContain("summaryText: ''") })
    test('Persona stores editing state', () => { expect(card).toContain('editing: false') })
    test('Persona calls getTwinPersona on mount', () => { expect(card).toContain('getTwinPersona'); expect(card).toContain('loadPersona') })
    test('Persona feeds summary to template', () => { expect(card).toContain('{{ summaryText }}') })
    test('Edit mode binds v-model to editText', () => { expect(card).toContain('v-model="editText"') })
    test('Save calls overrideTwinPersona', () => { expect(card).toContain('overrideTwinPersona') })
    test('Feedback calls feedbackTwinPersona', () => { expect(card).toContain('feedbackTwinPersona') })
  })

  // ===== Health data flow =====
  describe('Health data flow', () => {
    const health = readSource('../../src/pages/oj/views/user/twin/LearningHealthCard.vue')
    test('Health stores overall mastery', () => { expect(health).toContain('overall: 0') })
    test('Health computes masteryPercent', () => { expect(health).toContain('masteryPercent') })
    test('Health computes masteryColor', () => { expect(health).toContain('masteryColor') })
    test('Health computes ringDash for SVG', () => { expect(health).toContain('ringDash'); expect(health).toContain('circumference') })
    test('Health computes sparkPoints for polyline', () => { expect(health).toContain('sparkPoints'); expect(health).toContain('polyline') })
    test('Health maps due reviews to list items', () => { expect(health).toContain('v-for="item in dueReviews"') })
    test('Due review links to review-package', () => { expect(health).toContain("name: 'error-review-package'") })
  })

  // ===== Museum data flow =====
  describe('Museum data flow', () => {
    const museum = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumView.vue')
    const exhibit = readSource('../../src/pages/oj/views/user/twin/ErrorMuseumExhibit.vue')
    test('Museum stores pins array', () => { expect(museum).toContain('pins: []') })
    test('Museum pads to 9 slots', () => { expect(museum).toContain('paddedPins'); expect(museum).toContain('result.length < 9') })
    test('Museum passes pin to Exhibit', () => { expect(museum).toContain(':pin="pin"') })
    test('Exhibit handles null pin (empty state)', () => { expect(exhibit).toContain('v-if="pin"'); expect(exhibit).toContain('v-else') })
    test('Exhibit emits unpin with pinId', () => { expect(exhibit).toContain("$emit('unpin', pin.pin_id)") })
    test('Exhibit emits update-annotation', () => { expect(exhibit).toContain("$emit('update-annotation'") })
  })

  // ===== Chat data flow =====
  describe('Chat data flow', () => {
    const chat = readSource('../../src/pages/oj/views/user/twin/TwinChatPanel.vue')
    test('Chat stores messages array', () => { expect(chat).toContain('messages: []') })
    test('Chat pushes user message', () => { expect(chat).toContain("role: 'user'") })
    test('Chat pushes twin response', () => { expect(chat).toContain("role: 'twin'") })
    test('Chat binds input v-model', () => { expect(chat).toContain('v-model="inputText"') })
    test('Chat handles Enter key', () => { expect(chat).toContain('@keydown.enter') })
    test('Chat disables during sending', () => { expect(chat).toContain(':disabled="!inputText.trim() || sending"') })
    test('Chat auto-scrolls via ref', () => { expect(chat).toContain("this.$refs.messagesEl") })
  })

  // ===== Metacog data flow =====
  describe('Metacog data flow', () => {
    const predict = readSource('../../src/pages/oj/views/problem/PredictBeforeCodeCard.vue')
    const map = readSource('../../src/pages/oj/views/user/twin/MetacognitiveMapView.vue')
    test('Predict stores prediction states', () => { expect(predict).toContain('submitted: false'); expect(predict).toContain('collapsed: false') })
    test('Predict calls submitMetacogPrediction', () => { expect(predict).toContain('submitMetacogPrediction') })
    test('Predict stores eventId after submit', () => { expect(predict).toContain('eventId') })
    test('Map stores totalPredicts', () => { expect(map).toContain('totalPredicts: 0') })
    test('Map gates on 5 predictions', () => { expect(map).toContain('totalPredicts < 5') })
    test('Map renders heat bars', () => { expect(map).toContain('mc-map__heat-fill') })
  })

  // ===== Teach AI data flow =====
  describe('Teach AI data flow', () => {
    const teach = readSource('../../src/pages/oj/views/problem/TeachAiCard.vue')
    test('Teach stores session flow state', () => { expect(teach).toContain('started: false'); expect(teach).toContain('completed: false') })
    test('Teach calls startTeachAiSession', () => { expect(teach).toContain('startTeachAiSession') })
    test('Teach calls submitTeachAiExplanation', () => { expect(teach).toContain('submitTeachAiExplanation') })
    test('Teach stores score and feedback', () => { expect(teach).toContain('score: 0'); expect(teach).toContain("feedback: ''") })
    test('Teach min 10 chars validation', () => { expect(teach).toContain('explanation.trim().length < 10') })
    test('Teach has addHint helper', () => { expect(teach).toContain('addHint') })
  })

  // ===== Replay data flow =====
  describe('Replay data flow', () => {
    const replay = readSource('../../src/pages/oj/views/user/twin/CodeReplayPlayer.vue')
    test('Replay stores frames array', () => { expect(replay).toContain('frames: []') })
    test('Replay computes currentCode from frame', () => { expect(replay).toContain('currentCode') })
    test('Replay has play/pause toggle', () => { expect(replay).toContain('togglePlay'); expect(replay).toContain('playing: false') })
    test('Replay uses setInterval for playback', () => { expect(replay).toContain('setInterval') })
    test('Replay clears interval on unmount', () => { expect(replay).toContain('clearInterval'); expect(replay).toContain('beforeUnmount') })
    test('Replay has range slider', () => { expect(replay).toContain('type="range"') })
  })

  // ===== Weekly data flow =====
  describe('Weekly data flow', () => {
    const weekly = readSource('../../src/pages/oj/views/user/twin/TwinWeeklyReflection.vue')
    test('Weekly stores weekly data', () => { expect(weekly).toContain('weekly: {}') })
    test('Weekly stores reflectionText', () => { expect(weekly).toContain("reflectionText: ''") })
    test('Weekly calls getTwinWeekly', () => { expect(weekly).toContain('getTwinWeekly') })
    test('Weekly calls submitSundayReflection', () => { expect(weekly).toContain('submitSundayReflection') })
    test('Weekly has char count', () => { expect(weekly).toContain('reflectionText.length') })
  })

  // ===== Decay data flow =====
  describe('Decay data flow', () => {
    const decay = readSource('../../src/pages/oj/views/user/twin/TwinReviewQueue.vue')
    test('Decay stores fading/forgotten', () => { expect(decay).toContain('fading: []'); expect(decay).toContain('forgotten: []') })
    test('Decay calls getKcDecayQueue', () => { expect(decay).toContain('getKcDecayQueue') })
    test('Decay calls reviewDecayKc', () => { expect(decay).toContain('reviewDecayKc') })
    test('Decay removes reviewed item', () => { expect(decay).toContain('.filter(') })
  })

  // ===== What-If data flow =====
  describe('What-If data flow', () => {
    const whatif = readSource('../../src/pages/oj/views/user/twin/WhatIfBranchView.vue')
    test('WhatIf stores scenario state', () => { expect(whatif).toContain('scenario: null') })
    test('WhatIf stores result', () => { expect(whatif).toContain('result: null') })
    test('WhatIf calls getWhatIfBranch', () => { expect(whatif).toContain('getWhatIfBranch') })
    test('WhatIf shows affected KCs', () => { expect(whatif).toContain('affected_kcs') })
  })

  // ===== World Setting data flow =====
  describe('World Setting data flow', () => {
    const world = readSource('../../src/pages/oj/views/user/twin/WorldSettingPanel.vue')
    test('World stores worldName', () => { expect(world).toContain("worldName: '编程学院'") })
    test('World stores selectedTheme', () => { expect(world).toContain("selectedTheme: 'academy'") })
    test('World has 6 themes', () => { expect(world).toContain("id: 'academy'"); expect(world).toContain("id: 'sakura'") })
    test('World calls getWorldSetting', () => { expect(world).toContain('getWorldSetting') })
    test('World calls updateWorldSetting', () => { expect(world).toContain('updateWorldSetting') })
  })

  // ===== Public Profile data flow =====
  describe('Public Profile data flow', () => {
    const pub = readSource('../../src/pages/oj/views/user/twin/PublicTwinProfilePage.vue')
    test('Profile stores notFound', () => { expect(pub).toContain('notFound: false') })
    test('Profile stores profile data', () => { expect(pub).toContain('profile: {}') })
    test('Profile calls getPublicProfile', () => { expect(pub).toContain('getPublicProfile') })
    test('Profile shows conditional museum', () => { expect(pub).toContain('v-if="profile.museum') })
    test('Profile shows conditional persona', () => { expect(pub).toContain('v-if="profile.persona_text"') })
  })

  // ===== Dashboard orchestration =====
  describe('Dashboard orchestration', () => {
    const dash = readSource('../../src/pages/oj/views/user/twin/TwinDashboardPage.vue')
    test('Dashboard imports all 5 child components', () => {
      for (const c of ['TwinHero', 'LearningTimeline', 'LearningHealthCard', 'KcGalaxyView', 'ErrorMuseumView']) {
        expect(dash).toContain(`import ${c} from`)
      }
    })
    test('Dashboard renders components in template', () => {
      for (const c of ['<TwinHero', '<LearningTimeline', '<LearningHealthCard', '<KcGalaxyView', '<ErrorMuseumView']) {
        expect(dash).toContain(c)
      }
    })
    test('Dashboard has scroll listener', () => { expect(dash).toContain("window.addEventListener('scroll'") })
    test('Dashboard removes scroll listener', () => { expect(dash).toContain("window.removeEventListener('scroll'") })
    test('Dashboard back-to-top threshold', () => { expect(dash).toContain('scrollY > 600') })
  })
})
