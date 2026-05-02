import DOMPurify from 'dompurify'

const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's',
  'code', 'pre', 'blockquote',
  'ul', 'ol', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'table', 'thead', 'tbody', 'tr', 'td', 'th',
  'img', 'a', 'span', 'div', 'hr', 'sub', 'sup'
]

const ALLOWED_ATTR = ['class', 'href', 'src', 'alt', 'title', 'target', 'rel', 'colspan', 'rowspan']

DOMPurify.addHook('afterSanitizeAttributes', function (node) {
  if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

export function sanitize (html) {
  if (!html) {
    return ''
  }
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false
  })
}

const sanitizeMixin = {
  methods: {
    sanitize
  }
}

export default sanitizeMixin
