const ALLOWED_TAGS = new Set([
  'A',
  'B',
  'BLOCKQUOTE',
  'BR',
  'CODE',
  'DIV',
  'EM',
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
  'HR',
  'I',
  'LI',
  'OL',
  'P',
  'PRE',
  'SPAN',
  'STRONG',
  'SUB',
  'SUP',
  'TABLE',
  'TBODY',
  'TD',
  'TH',
  'THEAD',
  'TR',
  'U',
  'UL',
])

const DROP_TAGS = new Set([
  'EMBED',
  'FORM',
  'IFRAME',
  'INPUT',
  'MATH',
  'NOSCRIPT',
  'OBJECT',
  'SCRIPT',
  'SELECT',
  'STYLE',
  'SVG',
  'TEMPLATE',
  'TEXTAREA',
])

const ALLOWED_ATTRS = new Map<string, Set<string>>([
  ['A', new Set(['href', 'title'])],
])

const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])

export function sanitizeUrl(url?: string | null): string | null {
  if (!url) {
    return null
  }

  try {
    const parsed = new URL(url, window.location.origin)
    if (!SAFE_PROTOCOLS.has(parsed.protocol)) {
      return null
    }

    return parsed.toString()
  } catch {
    return null
  }
}

export function sanitizeHtml(html?: string | null): string {
  if (!html) {
    return ''
  }

  if (typeof DOMParser === 'undefined') {
    return html
  }

  const document = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html')
  const root = document.body

  for (const element of Array.from(root.querySelectorAll('*'))) {
    sanitizeElement(element)
  }

  return root.innerHTML
}

function sanitizeElement(element: Element) {
  if (DROP_TAGS.has(element.tagName)) {
    element.remove()
    return
  }

  if (!ALLOWED_TAGS.has(element.tagName)) {
    unwrapElement(element)
    return
  }

  const allowedAttrs = ALLOWED_ATTRS.get(element.tagName) ?? new Set<string>()
  for (const attribute of Array.from(element.attributes)) {
    if (!allowedAttrs.has(attribute.name)) {
      element.removeAttribute(attribute.name)
    }
  }

  if (element.tagName === 'A') {
    const safeHref = sanitizeUrl(element.getAttribute('href'))
    if (!safeHref) {
      element.removeAttribute('href')
    } else {
      element.setAttribute('href', safeHref)
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }
  }
}

function unwrapElement(element: Element) {
  const parent = element.parentNode
  if (!parent) {
    return
  }

  while (element.firstChild) {
    parent.insertBefore(element.firstChild, element)
  }

  parent.removeChild(element)
}
