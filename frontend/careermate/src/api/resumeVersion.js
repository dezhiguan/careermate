import { API_BASE_URL, getAuthHeaders, handleUnauthorized, request } from './http'

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

export function deleteVersion(versionId) {
  return request(`/resume-version/${encodeURIComponent(versionId)}`, { method: 'DELETE' })
}

async function downloadBinary(path, fallbackName) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: getAuthHeaders(),
  })
  if (res.status === 401) {
    handleUnauthorized(null)
  }
  if (!res.ok) {
    let message = `下载失败: ${res.status}`
    try {
      const payload = await res.json()
      message = payload?.message || message
    } catch {
      try {
        const text = await res.text()
        if (text) message = text
      } catch {
        // keep default message
      }
    }
    throw new Error(message)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function safeFileName(name, extension) {
  const base = String(name || '简历')
    .trim()
    .replace(/[\\/:*?"<>|\r\n]+/g, '_')
    .replace(/\s+/g, ' ')
  return `${base || '简历'}.${extension}`
}

/**
 * 下载简历版本 PDF，触发浏览器保存文件。
 * @param {string} versionId
 * @param {string} versionName  用于拼文件名
 */
export async function downloadVersionPdf(versionId, versionName) {
  return downloadBinary(
    `/resume-version/${encodeURIComponent(versionId)}/export/pdf`,
    safeFileName(versionName, 'pdf'),
  )
}

export async function downloadVersionDocx(versionId, versionName) {
  return downloadBinary(
    `/resume-version/${encodeURIComponent(versionId)}/export/docx`,
    safeFileName(versionName, 'docx'),
  )
}
