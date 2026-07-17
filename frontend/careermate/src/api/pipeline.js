import { request } from './http'

/** 投递看板（按阶段分列）。 */
export function getPipelineBoard() {
  return request('/pipeline/board')
}

/** 开始一个投递机会（jd_id 去重）。 */
export function createApplication(payload) {
  return request('/pipeline/applications', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

/** 移动阶段。 */
export function updateApplicationStage(id, stage) {
  return request(`/pipeline/applications/${id}/stage`, {
    method: 'PATCH',
    body: JSON.stringify({ stage }),
  })
}

/** 归档（软删）。 */
export function archiveApplication(id) {
  return request(`/pipeline/applications/${id}`, { method: 'DELETE' })
}

/** 卡片改名（displayName 空则回退自动生成名）。 */
export function renameApplication(id, displayName) {
  return request(`/pipeline/applications/${id}/name`, {
    method: 'PATCH',
    body: JSON.stringify({ displayName }),
  })
}

/** 按 JD 一键确认流转阶段（Layer-2 确认卡的「确认」按钮）。 */
export function confirmStage(payload) {
  return request('/pipeline/confirm-stage', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}
