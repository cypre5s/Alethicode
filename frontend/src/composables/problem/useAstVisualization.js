import { ref, watch, nextTick, getCurrentInstance } from 'vue'
import { hierarchy, tree, select, zoom, zoomIdentity, linkVertical, event as d3event } from 'd3'

export function useAstVisualization () {
  const instance = getCurrentInstance()
  const vm = () => instance.proxy

  const astDialogVisible = ref(false)
  const astTreeData = ref(null)
  const hotspotTooltip = ref({
    visible: false, text: '', type: '', typeLabel: '', impact: 0, x: 0, y: 0
  })

  let _hotspotMarkers = []

  function handleOpenAstDialog (astTree) {
    astTreeData.value = astTree
    astDialogVisible.value = true
  }

  function getEditorCore () {
    var editorRef = typeof vm().getEditorRef === 'function' ? vm().getEditorRef() : null
    if (!editorRef) return null
    return editorRef.core || (editorRef.getCore ? editorRef.getCore() : editorRef.core) || (editorRef.$refs && editorRef.$refs.editorCore) || null
  }

  function applyHotspots (hotspots) {
    clearHotspots()
    if (!hotspots || !hotspots.length) return
    var core = getEditorCore()
    if (!core) return
    hotspots.forEach(function (hotspot) {
      var range = hotspot && Array.isArray(hotspot.line_range) ? hotspot.line_range : []
      var startLineNumber = Number(range[0])
      var endLineNumber = Number(range[1])
      if (!Number.isInteger(startLineNumber) || !Number.isInteger(endLineNumber)) return
      if (startLineNumber < 1 || endLineNumber < startLineNumber) return
      var startLine = startLineNumber - 1
      var endLine = Math.min(endLineNumber - 1, core.lineCount() - 1)
      if (startLine >= core.lineCount() || endLine < startLine) return
      for (var line = startLine; line <= endLine; line++) {
        core.addLineClass(line, 'background', 'cm-hotspot-line')
        _hotspotMarkers.push({ line: line, type: 'lineClass' })
      }
      var marker = document.createElement('div')
      marker.className = 'hotspot-gutter-marker'
      var dot = document.createElement('span')
      dot.className = 'hotspot-dot'
      marker.appendChild(dot)
      marker.title = hotspot.suggestion
      marker.addEventListener('mouseenter', function (e) { showHotspotTooltip(e, hotspot) })
      marker.addEventListener('mouseleave', function () { hideHotspotTooltip() })
      marker.addEventListener('click', function () { navigateToLine(startLineNumber) })
      core.setGutterMarker(startLine, 'hotspot-gutter', marker)
      _hotspotMarkers.push({ line: startLine, type: 'gutter' })
    })
  }

  function clearHotspots () {
    var core = getEditorCore()
    if (!core) return
    _hotspotMarkers.forEach(function (m) {
      if (m.type === 'lineClass') core.removeLineClass(m.line, 'background', 'cm-hotspot-line')
      else if (m.type === 'gutter') core.setGutterMarker(m.line, 'hotspot-gutter', null)
    })
    _hotspotMarkers = []
  }

  function navigateToLine (lineNumber) {
    var core = getEditorCore()
    if (core) {
      var lineNumberInt = Number(lineNumber)
      if (!Number.isInteger(lineNumberInt) || lineNumberInt < 1) return
      var line = lineNumberInt - 1
      if (line >= core.lineCount()) return
      core.setCursor(line, 0)
      core.scrollToLine(line, 100)
      core.addLineClass(line, 'background', 'cm-flash-line')
      setTimeout(function () { core.removeLineClass(line, 'background', 'cm-flash-line') }, 1500)
    }
  }

  function showHotspotTooltip (event, hotspot) {
    var typeLabels = { 'PERFORMANCE_GAP': 'Performance Gap', 'EXPONENTIAL_GROWTH': 'Exponential Growth', 'REDUNDANT_COMPUTATION': 'Redundant Computation' }
    hotspotTooltip.value = {
      visible: true, text: hotspot.suggestion, type: hotspot.hotspot_type,
      typeLabel: typeLabels[hotspot.hotspot_type] || hotspot.hotspot_type,
      impact: hotspot.impact_percentage, x: event.clientX + 12, y: event.clientY - 10
    }
  }

  function hideHotspotTooltip () {
    hotspotTooltip.value = { ...hotspotTooltip.value, visible: false }
  }

  function renderASTTree () {
    var container = vm().$refs.astContainer
    if (!container || !astTreeData.value) return
    while (container.firstChild) container.removeChild(container.firstChild)
    var data = astTreeData.value
    var width = container.clientWidth || 800
    var height = container.clientHeight || 550
    var nodeColors = {
      'Module': '#64748B', 'FunctionDef': '#3B82F6', 'AsyncFunctionDef': '#3B82F6',
      'If': '#F59E0B', 'Compare': '#F59E0B', 'For': '#10B981', 'While': '#10B981',
      'Return': '#8B5CF6', 'Call': '#EF4444', 'BinOp': '#EC4899', 'ListComp': '#06B6D4'
    }
    var defaultColor = '#94A3B8'
    var childAccessor = function (d) {
      var children = []
      if (d.body && Array.isArray(d.body)) d.body.forEach(function (c) { if (c && typeof c === 'object' && c.type) children.push(c) })
      if (d.children && Array.isArray(d.children)) d.children.forEach(function (c) { if (c && typeof c === 'object' && c.type) children.push(c) })
      if (d.orelse && Array.isArray(d.orelse)) d.orelse.forEach(function (c) { if (c && typeof c === 'object' && c.type) children.push(c) })
      if (d.value && typeof d.value === 'object' && d.value.type) children.push(d.value)
      if (d.left && typeof d.left === 'object' && d.left.type) children.push(d.left)
      if (d.right && typeof d.right === 'object' && d.right.type) children.push(d.right)
      return children
    }
    var root = hierarchy(data, childAccessor)
    var treeLayout = tree()
      .size([width - 120, Math.max(height - 120, root.height * 80)])
      .separation(function (a, b) { return a.parent === b.parent ? 1.2 : 2 })
    treeLayout(root)
    var svg = select(container).append('svg').attr('width', width).attr('height', height).style('background', '#0F172A').style('border-radius', '8px')
    var g = svg.append('g').attr('transform', 'translate(60, 60)')
    var zoomBehavior = zoom().scaleExtent([0.3, 3]).on('zoom', function () { g.attr('transform', d3event.transform) })
    svg.call(zoomBehavior)
    svg.call(zoomBehavior.transform, zoomIdentity.translate(60, 60))
    g.selectAll('.ast-link').data(root.links()).enter().append('path')
      .attr('fill', 'none').attr('stroke', '#334155').attr('stroke-width', 1.5)
      .attr('d', linkVertical().x(function (d) { return d.x }).y(function (d) { return d.y }))
      .attr('stroke-dasharray', function () { return this.getTotalLength() })
      .attr('stroke-dashoffset', function () { return this.getTotalLength() })
      .transition().duration(800).delay(function (d, i) { return i * 30 }).attr('stroke-dashoffset', 0)
    var nodeGroups = g.selectAll('.ast-node').data(root.descendants()).enter().append('g')
      .attr('transform', function (d) { return 'translate(' + d.x + ',' + d.y + ')' })
      .style('cursor', function (d) { return d.data.location ? 'pointer' : 'default' })
      .on('click', function (d) {
        if (d.data.location && d.data.location.line) {
          navigateToLine(d.data.location.line)
          astDialogVisible.value = false
        }
      })
    nodeGroups.append('circle').attr('r', 0)
      .attr('fill', function (d) { return nodeColors[d.data.type] || defaultColor })
      .attr('stroke', '#1E293B').attr('stroke-width', 2)
      .transition().duration(500).delay(function (d, i) { return i * 20 })
      .attr('r', function (d) { return d.data.location ? 8 : 5 })
    nodeGroups.filter(function (d) { return !!d.data.location })
      .append('circle').attr('r', 13).attr('fill', 'none')
      .attr('stroke', function (d) { return nodeColors[d.data.type] || defaultColor })
      .attr('stroke-width', 1).attr('opacity', 0.3)
    nodeGroups.append('text')
      .attr('dy', function (d) { return d.children ? -16 : 20 })
      .attr('text-anchor', 'middle').attr('fill', '#CBD5E1').attr('font-size', '11px')
      .attr('font-family', "'JetBrains Mono', monospace")
      .text(function (d) { var name = d.data.name || d.data.type; return name.length > 18 ? name.substring(0, 18) + '…' : name })
      .attr('opacity', 0).transition().duration(300).delay(function (d, i) { return i * 20 + 300 }).attr('opacity', 1)
    nodeGroups
      .on('mouseover', function (d) {
        select(this).select('circle').transition().duration(200).attr('r', d.data.location ? 11 : 7)
        select(this).select('text').transition().duration(200).attr('fill', '#F8FAFC')
      })
      .on('mouseout', function (d) {
        select(this).select('circle').transition().duration(200).attr('r', d.data.location ? 8 : 5)
        select(this).select('text').transition().duration(200).attr('fill', '#CBD5E1')
      })
  }

  watch(astDialogVisible, (val) => {
    if (val && astTreeData.value) {
      nextTick(() => { setTimeout(() => renderASTTree(), 200) })
    }
  })

  return {
    astDialogVisible, astTreeData, hotspotTooltip,
    handleOpenAstDialog, getEditorCore, applyHotspots, clearHotspots,
    navigateToLine, showHotspotTooltip, hideHotspotTooltip, renderASTTree
  }
}
