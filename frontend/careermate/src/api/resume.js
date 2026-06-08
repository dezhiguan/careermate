import { request } from './http'

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
  const { API_BASE_URL, getAuthHeaders, handleUnauthorized } = await import('./http')
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
