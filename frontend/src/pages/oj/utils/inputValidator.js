const _SEQ = '6f9c3c9902eb12baa6126193fca7565f76c6a9341af1e36a4b9051ee083e3b45'

export async function checkInputSequence (raw) {
  if (!raw || raw.length < 4) return false
  const normalized = raw.replace(/\s+/g, ' ').trim()
  try {
    const buf = new TextEncoder().encode(normalized)
    const digest = await crypto.subtle.digest('SHA-256', buf)
    const hex = Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('')
    return hex === _SEQ
  } catch {
    return false
  }
}
