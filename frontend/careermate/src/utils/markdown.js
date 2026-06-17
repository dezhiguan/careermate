import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true,
  async: false,
})

export function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function renderMarkdown(text) {
  if (text == null || text === '') return ''
  const raw = typeof text === 'string' ? text : String(text)
  try {
    const html = marked.parse(raw, { async: false })
    if (html instanceof Promise) return `<p>${escapeHtml(raw)}</p>`
    return String(html)
  } catch {
    return `<p>${escapeHtml(raw).replace(/\n/g, '<br>')}</p>`
  }
}

export function stripMarkdown(text) {
  if (text == null || text === '') return ''
  return String(text)
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/>/g, ' · ')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/[*_~]{1,3}/g, '')
    .replace(/[\p{Extended_Pictographic}\uFE0F]/gu, '')
    .replace(/\|/g, ' ')
    .replace(/\s*·\s*·\s*/g, ' · ')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
