import { API_BASE_URL, request } from './http'

export async function listVersions(sessionId) {
  const qs = sessionId ? `?sessionId=${encodeURIComponent(sessionId)}` : ''
  return request(`/resume-version/list${qs}`, { method: 'GET' })
}

export async function getVersion(versionId) {
  return request(`/resume-version/${encodeURIComponent(versionId)}`, { method: 'GET' })
}

export async function updateVersion(versionId, payload) {
  return request(`/resume-version/${encodeURIComponent(versionId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/**
 * 下载简历版本 PDF，触发浏览器保存文件。
 * @param {string} versionId
 * @param {string} versionName  用于拼文件名
 */
export async function downloadVersionPdf(versionId, versionName) {
  const res = await fetch(`${API_BASE_URL}/resume-version/${encodeURIComponent(versionId)}/export/pdf`)
  if (!res.ok) throw new Error('PDF 下载失败')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${versionName || '简历'}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}
