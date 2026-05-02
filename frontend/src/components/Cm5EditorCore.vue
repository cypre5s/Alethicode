<template>
  <div ref="host" class="cm5-editor-core"></div>
</template>

<script>
import { EditorState, StateField, StateEffect, Compartment } from '@codemirror/state'
import { EditorView, Decoration, keymap, lineNumbers, highlightActiveLine, drawSelection, gutter, GutterMarker } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { syntaxHighlighting, defaultHighlightStyle, HighlightStyle, foldGutter, bracketMatching } from '@codemirror/language'
import { closeBrackets, closeBracketsKeymap, autocompletion, completionKeymap, completeFromList } from '@codemirror/autocomplete'
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search'
import { python } from '@codemirror/lang-python'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { javascript } from '@codemirror/lang-javascript'
import { go } from '@codemirror/lang-go'
import { oneDark } from '@codemirror/theme-one-dark'
import { tags } from '@lezer/highlight'

const LANGUAGE_MAP = {
  'text/x-python': () => python(),
  'text/x-csrc': () => cpp(),
  'text/x-c++src': () => cpp(),
  'text/x-java': () => java(),
  'text/javascript': () => javascript(),
  'text/x-go': () => go(),
  'text/plain': () => []
}

const LANGUAGE_COMPLETION_MAP = {
  Python3: [
    { label: 'def', type: 'keyword', apply: 'def function_name():\n    pass' },
    { label: 'if', type: 'keyword', apply: 'if condition:\n    pass' },
    { label: 'for', type: 'keyword', apply: 'for item in items:\n    pass' },
    { label: 'while', type: 'keyword', apply: 'while condition:\n    pass' },
    { label: 'class', type: 'keyword', apply: 'class ClassName:\n    pass' },
    { label: 'import', type: 'keyword', apply: 'import module_name' },
    { label: 'print', type: 'function', apply: 'print()' },
    { label: 'input', type: 'function', apply: 'input()' }
  ],
  C: [
    { label: '#include <stdio.h>', type: 'keyword', apply: '#include <stdio.h>' },
    { label: 'int main', type: 'function', apply: 'int main(void) {\n    return 0;\n}' },
    { label: 'printf', type: 'function', apply: 'printf("");' },
    { label: 'scanf', type: 'function', apply: 'scanf("");' },
    { label: 'for', type: 'keyword', apply: 'for (int i = 0; i < n; i++) {\n    \n}' },
    { label: 'while', type: 'keyword', apply: 'while (condition) {\n    \n}' },
    { label: 'if', type: 'keyword', apply: 'if (condition) {\n    \n}' }
  ],
  'C++': [
    { label: '#include <iostream>', type: 'keyword', apply: '#include <iostream>' },
    { label: 'using namespace std', type: 'keyword', apply: 'using namespace std;' },
    { label: 'vector', type: 'type', apply: 'vector<int> nums;' },
    { label: 'string', type: 'type', apply: 'string s;' },
    { label: 'cout', type: 'function', apply: 'cout << ;' },
    { label: 'cin', type: 'function', apply: 'cin >> ;' },
    { label: 'for', type: 'keyword', apply: 'for (int i = 0; i < n; i++) {\n    \n}' }
  ],
  Java: [
    { label: 'public class Main', type: 'keyword', apply: 'public class Main {\n    public static void main(String[] args) throws Exception {\n        \n    }\n}' },
    { label: 'public static void main', type: 'function', apply: 'public static void main(String[] args) throws Exception {\n    \n}' },
    { label: 'Scanner', type: 'type', apply: 'Scanner scanner = new Scanner(System.in);' },
    { label: 'BufferedReader', type: 'type', apply: 'BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));' },
    { label: 'System.out.println', type: 'function', apply: 'System.out.println();' },
    { label: 'for', type: 'keyword', apply: 'for (int i = 0; i < n; i++) {\n    \n}' },
    { label: 'if', type: 'keyword', apply: 'if (condition) {\n    \n}' }
  ]
}

LANGUAGE_COMPLETION_MAP.Python2 = LANGUAGE_COMPLETION_MAP.Python3
LANGUAGE_COMPLETION_MAP.Python = LANGUAGE_COMPLETION_MAP.Python3

const THEME_MAP = {
  solarized: 'light',
  monokai: 'dark',
  material: 'dark'
}

const addTextMark = StateEffect.define()
const clearTextMarks = StateEffect.define()
const addLineClassEffect = StateEffect.define()
const removeLineClassEffect = StateEffect.define()
const setGutterMarkerEffect = StateEffect.define()
const clearGutterEffect = StateEffect.define()

const textMarkField = StateField.define({
  create () { return Decoration.none },
  update (marks, tr) {
    marks = marks.map(tr.changes)
    for (const e of tr.effects) {
      if (e.is(addTextMark)) {
        marks = marks.update({ add: [e.value] })
      }
      if (e.is(clearTextMarks)) {
        marks = Decoration.none
      }
    }
    return marks
  },
  provide: f => EditorView.decorations.from(f)
})

const lineClassField = StateField.define({
  create () { return Decoration.none },
  update (decos, tr) {
    decos = decos.map(tr.changes)
    for (const e of tr.effects) {
      if (e.is(addLineClassEffect)) {
        decos = decos.update({ add: [e.value] })
      }
      if (e.is(removeLineClassEffect)) {
        const { from, cls } = e.value
        decos = decos.update({
          filter: (f, t, deco) => {
            if (f !== from) return true
            const spec = deco.spec
            return !(spec && spec.attributes && spec.attributes.class && spec.attributes.class.includes(cls))
          }
        })
      }
    }
    return decos
  },
  provide: f => EditorView.decorations.from(f)
})

class DomGutterMarker extends GutterMarker {
  constructor (dom) {
    super()
    this._dom = dom
  }

  toDOM () { return this._dom.cloneNode(true) }
}

const gutterMarkerField = StateField.define({
  create () { return new Map() },
  update (map, tr) {
    let nextMap = new Map(map)
    if (tr.docChanged) {
      const fresh = new Map()
      nextMap.forEach((marker, pos) => {
        const newPos = tr.changes.mapPos(pos, 1, 1)
        if (newPos !== null && newPos <= tr.newDoc.length) fresh.set(newPos, marker)
      })
      nextMap = fresh
    }
    for (const e of tr.effects) {
      if (e.is(setGutterMarkerEffect)) {
        const { pos, marker } = e.value
        if (marker) nextMap.set(pos, marker)
        else nextMap.delete(pos)
      }
      if (e.is(clearGutterEffect)) {
        nextMap = new Map()
      }
    }
    return nextMap
  }
})

function hotspotGutter () {
  return gutter({
    class: 'hotspot-gutter',
    markers: (view) => {
      const map = view.state.field(gutterMarkerField, false)
      if (!map || map.size === 0) return []
      const result = []
      map.forEach((marker, pos) => {
        result.push(marker.at(pos))
      })
      result.sort((a, b) => a.from - b.from)
      return {
        [Symbol.iterator]: function * () { yield * result }
      }
    },
    lineMarker: (view, line) => {
      const map = view.state.field(gutterMarkerField, false)
      if (!map) return null
      return map.get(line.from) || null
    }
  })
}

const solarizedLightHighlightStyle = HighlightStyle.define([
  { tag: [tags.keyword, tags.operatorKeyword, tags.controlKeyword, tags.definitionKeyword, tags.moduleKeyword], color: '#0000ff', fontWeight: '600' },
  { tag: [tags.atom, tags.bool, tags.null], color: '#0000ff' },
  { tag: [tags.number, tags.integer, tags.float], color: '#098658' },
  { tag: [tags.string, tags.special(tags.string)], color: '#a31515' },
  { tag: tags.regexp, color: '#811f3f' },
  { tag: [tags.comment, tags.lineComment, tags.blockComment, tags.docComment], color: '#008000', fontStyle: 'italic' },
  { tag: [tags.definition(tags.variableName), tags.definition(tags.propertyName)], color: '#001080' },
  { tag: [tags.function(tags.variableName), tags.function(tags.propertyName)], color: '#795e26' },
  { tag: [tags.variableName, tags.propertyName, tags.name, tags.attributeName], color: '#001080' },
  { tag: [tags.typeName, tags.className, tags.namespace, tags.labelName], color: '#267f99' },
  { tag: [tags.operator, tags.arithmeticOperator, tags.logicOperator, tags.compareOperator, tags.definitionOperator, tags.typeOperator], color: '#000000' },
  { tag: [tags.punctuation, tags.bracket, tags.separator], color: '#383a42' },
  { tag: [tags.meta, tags.docString], color: '#795e26' },
  { tag: tags.invalid, color: '#cd3131', textDecoration: 'underline wavy' }
])

const solarizedLightTheme = EditorView.theme({
  '&': { backgroundColor: '#ffffff', color: '#24292e' },
  '.cm-content': { caretColor: '#24292e' },
  '.cm-cursor': { borderLeftColor: '#24292e' },
  '.cm-activeLine': { backgroundColor: '#f6f8fa' },
  '.cm-gutters': { backgroundColor: '#f6f8fa', color: '#6e7781', borderRight: '1px solid #e1e4e8' },
  '.cm-activeLineGutter': { backgroundColor: '#e1e4e8' }
})

export default {
  name: 'Cm5EditorCore',
  props: {
    initialValue: {
      type: String,
      default: ''
    },
    options: {
      type: Object,
      default: () => ({})
    }
  },
  emits: ['change', 'ready', 'focus', 'blur'],
  data () {
    return {
      editor: null,
      suppressChange: false,
      _changeSubscribers: [],
      _markCounter: 0
    }
  },
  mounted () {
    this._langCompartment = new Compartment()
    this._completionCompartment = new Compartment()
    this._themeCompartment = new Compartment()
    this._readOnlyCompartment = new Compartment()
    this._tabSizeCompartment = new Compartment()
    this._lineWrapCompartment = new Compartment()
    this.initializeEditor()
  },
  beforeUnmount () {
    if (this.editor) {
      this.editor.destroy()
      this.editor = null
    }
    this._changeSubscribers = []
  },
  watch: {
    options: {
      deep: true,
      handler (nextOptions) {
        if (!this.editor) return
        if (nextOptions.mode) this.setOption('mode', nextOptions.mode)
        if (nextOptions.completionProfile || nextOptions.language) {
          this.setOption('completionProfile', nextOptions.completionProfile || nextOptions.language)
        }
        if (nextOptions.theme) this.setOption('theme', nextOptions.theme)
        if (nextOptions.tabSize) this.setOption('tabSize', nextOptions.tabSize)
        if (typeof nextOptions.lineWrapping === 'boolean') this.setOption('lineWrapping', nextOptions.lineWrapping)
        if (typeof nextOptions.readOnly === 'boolean') this.setOption('readOnly', nextOptions.readOnly)
      }
    }
  },
  methods: {
    initializeEditor () {
      const opts = this.options || {}
      const langExt = this.resolveLanguage(opts.mode)
      const themeExt = this.resolveTheme(opts.theme)

      const extensions = [
        lineNumbers(),
        history(),
        foldGutter(),
        drawSelection(),
        bracketMatching(),
        closeBrackets(),
        highlightActiveLine(),
        highlightSelectionMatches(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        keymap.of([
          { key: 'Mod-Enter', run: () => { this.$emit('submit'); return true } },
          { key: 'Mod-Shift-Enter', run: () => { this.$emit('debug'); return true } },
          ...closeBracketsKeymap,
          ...completionKeymap,
          ...defaultKeymap,
          ...searchKeymap,
          ...historyKeymap,
          indentWithTab
        ]),
        this._langCompartment.of(langExt),
        this._completionCompartment.of(this.resolveCompletionProfile(opts.completionProfile || opts.language || '')),
        this._themeCompartment.of(themeExt),
        this._readOnlyCompartment.of(EditorState.readOnly.of(!!opts.readOnly)),
        this._tabSizeCompartment.of(EditorState.tabSize.of(opts.tabSize || 4)),
        this._lineWrapCompartment.of(opts.lineWrapping ? EditorView.lineWrapping : []),
        textMarkField,
        lineClassField,
        gutterMarkerField,
        hotspotGutter(),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !this.suppressChange) {
            const text = update.state.doc.toString()
            this.$emit('change', text)
            this._changeSubscribers.forEach(fn => {
              try { fn(update) } catch (e) { console.warn('[Cm5EditorCore] change subscriber failed:', e) }
            })
          }
          if (update.focusChanged) {
            this.$emit(update.view.hasFocus ? 'focus' : 'blur')
          }
        }),
        EditorView.theme({
          '&': { height: '100%' },
          '.cm-scroller': { overflow: 'auto' },
          '.cm-content': {
            fontFamily: "Consolas, 'Courier New', monospace",
            fontSize: '14px'
          },
          '.cm-gutters': {
            fontFamily: "Consolas, 'Courier New', monospace",
            fontSize: '14px'
          }
        })
      ]

      this.editor = new EditorView({
        state: EditorState.create({
          doc: this.normalizeText(this.initialValue),
          extensions
        }),
        parent: this.$refs.host
      })

      this.$emit('ready', this.editor)
    },

    resolveLanguage (mode) {
      const factory = LANGUAGE_MAP[mode]
      if (factory) return factory()
      return []
    },

    resolveCompletionProfile (profile) {
      const completions = LANGUAGE_COMPLETION_MAP[profile]
      if (!completions || completions.length === 0) {
        return autocompletion({ override: [] })
      }
      return autocompletion({ override: [completeFromList(completions)] })
    },

    resolveTheme (theme) {
      if (THEME_MAP[theme] === 'dark') {
        return oneDark
      }
      return [solarizedLightTheme, syntaxHighlighting(solarizedLightHighlightStyle)]
    },

    withSuppressedChange (fn) {
      this.suppressChange = true
      try { return fn() } finally { this.suppressChange = false }
    },

    normalizeText (text) {
      return typeof text === 'string' ? text : ''
    },

    setDocument (text, config = {}) {
      if (!this.editor) return
      const nextText = this.normalizeText(text)
      const run = () => {
        this.editor.dispatch({
          changes: { from: 0, to: this.editor.state.doc.length, insert: nextText }
        })
        if (config.cursor) {
          const line = Math.min(Math.max(0, config.cursor.line || 0), this.editor.state.doc.lines - 1)
          const lineInfo = this.editor.state.doc.line(line + 1)
          const ch = Math.min(config.cursor.ch || 0, lineInfo.length)
          this.editor.dispatch({ selection: { anchor: lineInfo.from + ch } })
        }
        if (config.scroll) {
          this.editor.scrollDOM.scrollLeft = config.scroll.left || 0
          this.editor.scrollDOM.scrollTop = config.scroll.top || 0
        }
      }
      if (config.silent) {
        this.withSuppressedChange(run)
        return
      }
      run()
    },

    getDocument () {
      if (!this.editor) return ''
      return this.editor.state.doc.toString()
    },

    focus () {
      if (this.editor) this.editor.focus()
    },

    refreshLayout () {
      if (this.editor) this.editor.requestMeasure()
    },

    appendCode (text) {
      if (!this.editor) return
      const nextText = this.normalizeText(text)
      const doc = this.editor.state.doc
      const current = doc.toString()
      const sep = current.trim() ? '\n\n' : ''
      const trailingNewline = /\n$/.test(nextText) ? '' : '\n'
      const insertText = sep + nextText + trailingNewline

      this.editor.dispatch({
        changes: { from: doc.length, insert: insertText }
      })

      const prefixText = current + sep
      const preferredCursor = this.findPreferredAppendCursor(nextText, prefixText)
      const newDoc = this.editor.state.doc
      if (preferredCursor) {
        const lineNum = Math.min(preferredCursor.line + 1, newDoc.lines)
        const lineInfo = newDoc.line(lineNum)
        this.editor.dispatch({
          selection: { anchor: lineInfo.from },
          effects: EditorView.scrollIntoView(lineInfo.from, { y: 'center' })
        })
      } else {
        this.editor.dispatch({
          selection: { anchor: newDoc.length },
          effects: EditorView.scrollIntoView(newDoc.length, { y: 'center' })
        })
      }
      this.focus()
    },

    insertCodeAtCursor (text) {
      if (!this.editor) return
      const nextText = this.normalizeText(text)
      const pos = this.editor.state.selection.main.head
      this.editor.dispatch({
        changes: { from: pos, insert: nextText }
      })
      this.focus()
    },

    replaceLines (startLine, endLine, text) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const s = Number(startLine)
      const e = Number(endLine)
      if (!Number.isInteger(s) || !Number.isInteger(e)) throw new Error('replaceLines requires integer line numbers')
      if (s < 1 || e < s || e > doc.lines) throw new Error('replaceLines range is out of bounds')
      const nextText = this.normalizeText(text)
      const from = doc.line(s).from
      const to = doc.line(e).to
      this.editor.dispatch({ changes: { from, to, insert: nextText } })
      const newLineInfo = this.editor.state.doc.line(Math.min(s, this.editor.state.doc.lines))
      this.editor.dispatch({
        selection: { anchor: newLineInfo.from },
        effects: EditorView.scrollIntoView(newLineInfo.from, { y: 'center' })
      })
      this.focus()
    },

    setOption (name, value) {
      if (!this.editor) return
      if (name === 'mode') {
        this.editor.dispatch({ effects: this._langCompartment.reconfigure(this.resolveLanguage(value)) })
      } else if (name === 'completionProfile') {
        this.editor.dispatch({ effects: this._completionCompartment.reconfigure(this.resolveCompletionProfile(value)) })
      } else if (name === 'theme') {
        this.editor.dispatch({ effects: this._themeCompartment.reconfigure(this.resolveTheme(value)) })
      } else if (name === 'tabSize') {
        this.editor.dispatch({ effects: this._tabSizeCompartment.reconfigure(EditorState.tabSize.of(value)) })
      } else if (name === 'readOnly') {
        this.editor.dispatch({ effects: this._readOnlyCompartment.reconfigure(EditorState.readOnly.of(!!value)) })
      } else if (name === 'lineWrapping') {
        this.editor.dispatch({ effects: this._lineWrapCompartment.reconfigure(value ? EditorView.lineWrapping : []) })
      }
    },

    lineCount () {
      if (!this.editor) return 0
      return this.editor.state.doc.lines
    },

    getLine (n) {
      if (!this.editor) return ''
      const doc = this.editor.state.doc
      const lineNum = n + 1
      if (lineNum < 1 || lineNum > doc.lines) return ''
      return doc.line(lineNum).text
    },

    markText (from, to, opts) {
      if (!this.editor) return { clear () {} }
      const doc = this.editor.state.doc
      const fromLine = doc.line(from.line + 1)
      const toLine = doc.line(to.line + 1)
      const absFrom = fromLine.from + from.ch
      const absTo = toLine.from + to.ch

      const attrs = {}
      if (opts.className) attrs.class = opts.className
      if (opts.title) attrs.title = opts.title
      const deco = Decoration.mark({ attributes: attrs }).range(absFrom, absTo)
      const id = ++this._markCounter

      this.editor.dispatch({ effects: addTextMark.of(deco) })

      const self = this
      return {
        _id: id,
        clear () {
          if (!self.editor) return
          self.editor.dispatch({
            effects: clearTextMarks.of(null)
          })
        }
      }
    },

    addLineClass (line, where, cls) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const lineNum = line + 1
      if (lineNum < 1 || lineNum > doc.lines) return
      const lineInfo = doc.line(lineNum)
      const deco = Decoration.line({ attributes: { class: cls } }).range(lineInfo.from)
      this.editor.dispatch({ effects: addLineClassEffect.of(deco) })
    },

    removeLineClass (line, where, cls) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const lineNum = line + 1
      if (lineNum < 1 || lineNum > doc.lines) return
      const lineInfo = doc.line(lineNum)
      this.editor.dispatch({ effects: removeLineClassEffect.of({ from: lineInfo.from, cls }) })
    },

    setGutterMarker (line, gutterId, element) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const lineNum = line + 1
      if (lineNum < 1 || lineNum > doc.lines) return
      const lineInfo = doc.line(lineNum)
      const marker = element ? new DomGutterMarker(element) : null
      this.editor.dispatch({ effects: setGutterMarkerEffect.of({ pos: lineInfo.from, marker }) })
    },

    clearGutter (gutterId) {
      if (!this.editor) return
      this.editor.dispatch({ effects: clearGutterEffect.of(null) })
    },

    scrollToLine (line, margin) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const lineNum = line + 1
      if (lineNum < 1 || lineNum > doc.lines) return
      const lineInfo = doc.line(lineNum)
      this.editor.dispatch({
        effects: EditorView.scrollIntoView(lineInfo.from, { y: 'center' })
      })
    },

    setCursor (line, ch) {
      if (!this.editor) return
      const doc = this.editor.state.doc
      const lineNum = line + 1
      if (lineNum < 1 || lineNum > doc.lines) return
      const lineInfo = doc.line(lineNum)
      const pos = lineInfo.from + Math.min(ch || 0, lineInfo.length)
      this.editor.dispatch({ selection: { anchor: pos } })
    },

    onChangeSubscribe (fn) {
      this._changeSubscribers.push(fn)
      return {
        dispose: () => {
          const idx = this._changeSubscribers.indexOf(fn)
          if (idx >= 0) this._changeSubscribers.splice(idx, 1)
        }
      }
    },

    findPreferredAppendCursor (insertedCode, prefixText) {
      const normalizedCode = this.normalizeText(insertedCode)
      const normalizedPrefix = this.normalizeText(prefixText)
      const baseLine = (normalizedPrefix.match(/\n/g) || []).length
      const insertedLines = normalizedCode.split('\n')
      for (let i = 0; i < insertedLines.length; i++) {
        if (!/TODO/i.test(insertedLines[i])) continue
        const nextLine = insertedLines[i + 1]
        if (typeof nextLine === 'string' && nextLine.trim() === '') {
          return { line: baseLine + i + 1, ch: 0 }
        }
      }
      const firstBlankLine = insertedLines.findIndex(line => line.trim() === '')
      if (firstBlankLine >= 0) {
        return { line: baseLine + firstBlankLine, ch: 0 }
      }
      return null
    }
  }
}
</script>

<style scoped>
.cm5-editor-core {
  width: 100%;
  height: 350px;
}
</style>
