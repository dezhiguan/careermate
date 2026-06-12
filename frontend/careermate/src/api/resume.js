import { API_BASE_URL, getAuthHeaders, handleUnauthorized, request } from './http'

export function listResumes() {
  return request('/resumes', { method: 'GET' })
}

export function createResume(payload) {
  return request('/resumes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getResume(id) {
  return request(`/resumes/${id}`, { method: 'GET' })
}

export function updateResume(id, payload) {
  return request(`/resumes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteResume(id) {
  return request(`/resumes/${id}`, { method: 'DELETE' })
}

export function setDefaultResume(id) {
  return request(`/resumes/${id}/default`, { method: 'POST' })
}

export async function uploadResumeFile(file, title) {
  const formData = new FormData()
  formData.append('file', file)
  if (title && title.trim()) {
    formData.append('title', title.trim())
  }

  const response = await fetch(`${API_BASE_URL}/resumes/upload`, {
    method: 'POST',
    headers: getAuthHeaders(), // 只传 Authorization，不设 Content-Type
    body: formData,
  })

  let payload = null
  try { payload = await response.json() } catch (e) { payload = null }

  if (response.status === 401 || payload?.code === 401) {
    handleUnauthorized(payload)
  }
  if (!response.ok) {
    throw new Error(payload?.message || `上传失败: ${response.status}`)
  }
  if (!payload || payload.code !== 0) {
    throw new Error(payload?.message || '上传失败')
  }
  return payload.data
}

async function downloadBinary(path, fallbackName) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: getAuthHeaders(),
  })
  if (response.status === 401) {
    handleUnauthorized(null)
  }
  if (!response.ok) {
    let message = `下载失败: ${response.status}`
    try {
      const payload = await response.json()
      message = payload?.message || message
    } catch {
      try {
        const text = await response.text()
        if (text) message = text
      } catch {
        // keep default message
      }
    }
    throw new Error(message)
  }
  const blob = await response.blob()
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

export function downloadResumePdf(id, title) {
  return downloadBinary(`/resumes/${encodeURIComponent(id)}/export/pdf`, safeFileName(title, 'pdf'))
}

export function downloadResumeDocx(id, title) {
  return downloadBinary(`/resumes/${encodeURIComponent(id)}/export/docx`, safeFileName(title, 'docx'))
}
