import storage from '@/utils/storage'

export function workflowChatCacheKey (problemId) {
  if (!problemId) return ''
  return `workflowChat_NaN_${problemId}`
}

export function persistAgentMessagesCache (key, sessionId, agentMessages) {
  if (!key) return
  const messages = Array.isArray(agentMessages) ? agentMessages.slice(-100) : []
  storage.set(key, { session_id: sessionId || null, messages })
}

export function readCachedSessionId (key) {
  if (!key) return null
  const cached = storage.get(key)
  if (cached && typeof cached === 'object' && cached.session_id) {
    return cached.session_id
  }
  return null
}

export function readAgentMessagesCache (key, expectedSessionId = null) {
  if (!key) return null
  const cached = storage.get(key)
  let messages = []
  let cachedSessionId = null
  if (Array.isArray(cached)) {
    messages = cached
  } else if (cached && typeof cached === 'object' && Array.isArray(cached.messages)) {
    messages = cached.messages
    cachedSessionId = cached.session_id || null
  }
  if (!Array.isArray(messages) || messages.length === 0) return null
  if (expectedSessionId && cachedSessionId && cachedSessionId !== expectedSessionId) return null
  return messages
}

export function buildAgentMessageCacheSignature (message) {
  if (!message || typeof message !== 'object') return ''
  if (message.type === 'user' || message.type === 'ai_reply' || message.type === 'system' || message.type === 'error') {
    return JSON.stringify({ type: message.type || '', content: message.content || '' })
  }
  return JSON.stringify({ type: message.type || '', data: message.data || null })
}

export function mergeAgentMessagesCache (existingMessages, cachedMessages) {
  if (!cachedMessages) return null
  if (!Array.isArray(existingMessages) || existingMessages.length === 0) {
    return cachedMessages.slice()
  }
  const mergedMessages = cachedMessages.slice()
  const existingSignatures = new Set(cachedMessages.map(m => buildAgentMessageCacheSignature(m)))
  existingMessages.forEach(message => {
    const signature = buildAgentMessageCacheSignature(message)
    if (!signature || existingSignatures.has(signature)) return
    existingSignatures.add(signature)
    mergedMessages.push(message)
  })
  return mergedMessages
}

export function clearAgentMessagesCache (key) {
  if (!key) return
  storage.remove(key)
}
