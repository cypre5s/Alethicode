/**
 * 前端 AST Pre-flight 检测器
 *
 * 10 个基于文本/正则的检测器，在浏览器本地同步执行（< 10ms）。
 * 每个检测器返回 { hit: boolean, line_number: number, code_snippet: string }。
 * 检测器名称与后端 misconception_detectors.py 一一对应。
 */

const DETECTORS = []

function register (name, fn) {
  DETECTORS.push({ name, fn })
}

function getLines (code) {
  return code.split('\n')
}

// ─── input_returns_int ─────────────────────────────────────────
register('input_returns_int', function (code) {
  const lines = getLines(code)
  const inputVars = new Set()

  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^\s*(\w+)\s*=\s*input\s*\(/)
    if (m) inputVars.add(m[1])
  }
  if (inputVars.size === 0) return null

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    for (const v of inputVars) {
      const arithRe = new RegExp('\\b' + v + '\\b\\s*[+\\-*/%]|[+\\-*/%]\\s*\\b' + v + '\\b')
      if (arithRe.test(line)) {
        return { hit: true, line_number: i + 1, code_snippet: line.trim() }
      }
    }
  }
  return null
})

// ─── range_inclusive_end ────────────────────────────────────────
register('range_inclusive_end', function (code) {
  const lines = getLines(code)
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/range\s*\(\s*\w+\s*,\s*(\w+)\s*\)/)
    if (!m) continue
    const endArg = m[1]
    if (/^\d+$/.test(endArg)) {
      const n = parseInt(endArg, 10)
      if ([10, 100, 1000, 26, 52].includes(n)) {
        return { hit: true, line_number: i + 1, code_snippet: lines[i].trim() }
      }
    }
  }
  return null
})

// ─── missing_int_conversion ────────────────────────────────────
register('missing_int_conversion', function (code) {
  const lines = getLines(code)
  const rawInputVars = new Set()

  for (let i = 0; i < lines.length; i++) {
    const mInput = lines[i].match(/^\s*(\w+)\s*=\s*input\s*\(/)
    if (mInput) rawInputVars.add(mInput[1])
    const mConvert = lines[i].match(/^\s*(\w+)\s*=\s*(?:int|float)\s*\(/)
    if (mConvert) rawInputVars.delete(mConvert[1])
  }
  if (rawInputVars.size === 0) return null

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    for (const v of rawInputVars) {
      const cmpRe = new RegExp('\\b' + v + '\\b\\s*[<>=!]+\\s*\\d|\\d\\s*[<>=!]+\\s*\\b' + v + '\\b')
      if (cmpRe.test(line)) {
        return { hit: true, line_number: i + 1, code_snippet: line.trim() }
      }
    }
  }
  return null
})

// ─── global_var_no_keyword ─────────────────────────────────────
register('global_var_no_keyword', function (code) {
  const lines = getLines(code)
  const moduleVars = new Set()

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.match(/^(\w+)\s*=\s*/)) {
      moduleVars.add(line.match(/^(\w+)/)[1])
    }
  }
  if (moduleVars.size === 0) return null

  let inFunc = false
  let funcIndent = 0
  const globalNames = new Set()

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const stripped = line.trimStart()
    const indent = line.length - line.trimStart().length

    if (stripped.startsWith('def ')) {
      inFunc = true
      funcIndent = indent
      globalNames.clear()
      continue
    }

    if (inFunc && indent <= funcIndent && stripped.length > 0 && !stripped.startsWith('def ')) {
      inFunc = false
    }

    if (inFunc) {
      const gm = stripped.match(/^global\s+(.+)/)
      if (gm) {
        gm[1].split(',').forEach(n => globalNames.add(n.trim()))
      }
      const am = stripped.match(/^(\w+)\s*=\s*/)
      if (am) {
        const varName = am[1]
        if (moduleVars.has(varName) && !globalNames.has(varName)) {
          return { hit: true, line_number: i + 1, code_snippet: stripped }
        }
      }
    }
  }
  return null
})

// ─── append_vs_extend ──────────────────────────────────────────
register('append_vs_extend', function (code) {
  const lines = getLines(code)
  for (let i = 0; i < lines.length; i++) {
    if (/\.append\s*\(\s*\[/.test(lines[i])) {
      return { hit: true, line_number: i + 1, code_snippet: lines[i].trim() }
    }
  }
  return null
})

// ─── mutable_default_arg ───────────────────────────────────────
register('mutable_default_arg', function (code) {
  const lines = getLines(code)
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*def\s+\w+\s*\(.*=\s*\[\s*\]/.test(lines[i]) ||
        /^\s*def\s+\w+\s*\(.*=\s*\{\s*\}/.test(lines[i])) {
      return { hit: true, line_number: i + 1, code_snippet: lines[i].trim() }
    }
  }
  return null
})

// ─── equality_vs_assignment ────────────────────────────────────
register('equality_vs_assignment', function (code) {
  const lines = getLines(code)
  for (let i = 0; i < lines.length; i++) {
    const stripped = lines[i].trim()
    if (!stripped.startsWith('if ') && !stripped.startsWith('elif ')) continue
    const colonIdx = stripped.indexOf(':')
    if (colonIdx === -1) continue
    const cond = stripped.slice(stripped.indexOf(' ') + 1, colonIdx)
    if (cond.includes('==') || cond.includes('!=')) continue
    if (cond.includes('<=') || cond.includes('>=')) continue
    const eqIdx = cond.indexOf('=')
    if (eqIdx <= 0) continue
    if (cond[eqIdx - 1] === '<' || cond[eqIdx - 1] === '>' || cond[eqIdx - 1] === '!' || cond[eqIdx - 1] === '=') continue
    if (eqIdx + 1 < cond.length && cond[eqIdx + 1] === '=') continue
    return { hit: true, line_number: i + 1, code_snippet: stripped }
  }
  return null
})

// ─── dict_key_not_found ────────────────────────────────────────
register('dict_key_not_found', function (code) {
  const lines = getLines(code)
  const dictVars = new Set()
  const usesGet = new Set()

  for (let i = 0; i < lines.length; i++) {
    const dm = lines[i].match(/^\s*(\w+)\s*=\s*\{/)
    if (dm) dictVars.add(dm[1])
    const gm = lines[i].match(/(\w+)\.get\s*\(/)
    if (gm) usesGet.add(gm[1])
  }
  if (dictVars.size === 0) return null

  for (let i = 0; i < lines.length; i++) {
    for (const dv of dictVars) {
      if (usesGet.has(dv)) continue
      const subRe = new RegExp('\\b' + dv + '\\s*\\[\\s*\\w+\\s*\\]')
      if (subRe.test(lines[i])) {
        return { hit: true, line_number: i + 1, code_snippet: lines[i].trim() }
      }
    }
  }
  return null
})

// ─── file_not_closed ───────────────────────────────────────────
register('file_not_closed', function (code) {
  const lines = getLines(code)
  let hasWithOpen = false
  const openVars = new Set()
  const closedVars = new Set()

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (/^\s*with\s+open\s*\(/.test(line)) hasWithOpen = true
    const om = line.match(/^\s*(\w+)\s*=\s*open\s*\(/)
    if (om) openVars.add(om[1])
    const cm = line.match(/(\w+)\.close\s*\(/)
    if (cm) closedVars.add(cm[1])
  }
  if (openVars.size === 0) return null

  for (const v of openVars) {
    if (!closedVars.has(v) && !hasWithOpen) {
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].match(new RegExp('^\\s*' + v + '\\s*=\\s*open\\s*\\('))) {
          return { hit: true, line_number: i + 1, code_snippet: lines[i].trim() }
        }
      }
    }
  }
  return null
})

// ─── print_vs_return ───────────────────────────────────────────
register('print_vs_return', function (code) {
  const lines = getLines(code)
  let inFunc = false
  let funcIndent = 0
  let hasPrint = false
  let hasReturn = false
  let printLine = -1
  let printSnippet = ''

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const stripped = line.trimStart()
    const indent = line.length - stripped.length

    if (stripped.startsWith('def ')) {
      if (inFunc && hasPrint && !hasReturn && printLine >= 0) {
        return { hit: true, line_number: printLine + 1, code_snippet: printSnippet }
      }
      inFunc = true
      funcIndent = indent
      hasPrint = false
      hasReturn = false
      continue
    }

    if (inFunc && indent <= funcIndent && stripped.length > 0) {
      if (hasPrint && !hasReturn && printLine >= 0) {
        return { hit: true, line_number: printLine + 1, code_snippet: printSnippet }
      }
      inFunc = false
    }

    if (inFunc) {
      if (/\breturn\b/.test(stripped) && !/return\s*$/.test(stripped)) {
        hasReturn = true
      }
      if (/\bprint\s*\(/.test(stripped)) {
        hasPrint = true
        printLine = i
        printSnippet = stripped
      }
    }
  }

  if (inFunc && hasPrint && !hasReturn && printLine >= 0) {
    return { hit: true, line_number: printLine + 1, code_snippet: printSnippet }
  }
  return null
})

// ─── 公开 API ──────────────────────────────────────────────────

/**
 * 对代码运行全部检测器，返回命中列表。
 * @param {string} code - 学生代码
 * @returns {Array<{detector_name: string, line_number: number, code_snippet: string}>}
 */
export function runPreflightDetectors (code) {
  if (!code || !code.trim()) return []

  const hits = []
  for (const { name, fn } of DETECTORS) {
    try {
      const result = fn(code)
      if (result && result.hit) {
        hits.push({
          detector_name: name,
          line_number: result.line_number,
          code_snippet: result.code_snippet
        })
      }
    } catch (e) {
      // 检测器异常不阻断提交
    }
  }
  return hits
}

/**
 * 从命中列表中选择优先级最高的一个展示。
 * 优先级 = trigger_count 最低者（最陌生的错误优先）。
 * @param {Array} hits - runPreflightDetectors 返回的命中列表
 * @param {Object} triggerCounts - { detector_name: count } 映射
 * @returns {Object|null} 选中的命中项
 */
export function selectPriorityHit (hits, triggerCounts) {
  if (!hits || hits.length === 0) return null
  if (hits.length === 1) return hits[0]

  let best = hits[0]
  let bestCount = (triggerCounts && triggerCounts[best.detector_name]) || 0

  for (let i = 1; i < hits.length; i++) {
    const count = (triggerCounts && triggerCounts[hits[i].detector_name]) || 0
    if (count < bestCount) {
      best = hits[i]
      bestCount = count
    }
  }
  return best
}
