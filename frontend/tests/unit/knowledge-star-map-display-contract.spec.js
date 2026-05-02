/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('knowledge star map display contract', () => {
  const source = readSource('../../src/pages/oj/components/skillProfile/KnowledgeStarMap.vue')

  test('stats should render from displayStats instead of stale raw stats', () => {
    expect(source).toContain('displayStats.mastered_count')
    expect(source).toContain('displayStats.total_kcs')
    expect(source).toContain('displayStats.weak_count')
  })

  test('snapshot mastery should use explicit key existence check', () => {
    expect(source).toContain('Object.prototype.hasOwnProperty.call(this.snapshotMastery, nodeId)')
    expect(source).toContain('resolveNodeMastery (node)')
  })

  test('chapter filtering should normalize chapter keys and edge endpoints', () => {
    expect(source).toContain('normalizeChapterKey (value)')
    expect(source).toContain('getEdgeEndpointId (endpoint)')
    expect(source).toContain('this.isNodeInChapter(n, this.activeChapter)')
  })

  test('single chapter view should inject bridge edges to keep graph connected', () => {
    expect(source).toContain('buildConnectedChapterEdges (nodes, edges)')
    expect(source).toContain("relation: 'chapter_bridge'")
    expect(source).toContain('edges: this.buildConnectedChapterEdges(nodes, normalizedEdges)')
  })

  test('all view should not render chapter title labels in graph canvas', () => {
    expect(source).toContain('if (this.activeChapter) {')
    expect(source).toContain('g.selectAll(\'.ksm-chapter-label\')')
  })
})
