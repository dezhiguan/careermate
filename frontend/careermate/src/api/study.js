import { request } from './http'

/** 分页查询个人八股题库（可按技能 + 关键词过滤）。 */
export function listStudyNotes({ skill, untagged, keyword, page = 1, size = 8 } = {}) {
  const q = new URLSearchParams()
  // untagged 与 skill 互斥：只看未打标签的题时不带 skill
  if (untagged) q.set('untagged', 'true')
  else if (skill) q.set('skill', skill)
  if (keyword) q.set('keyword', keyword)
  q.set('page', String(page))
  q.set('size', String(size))
  return request(`/study/notes?${q.toString()}`)
}

/** 筛选面标签：预置标签 ∪ 用户自建标签，各带题数。 */
export function listStudySkills() {
  return request('/study/notes/skills')
}

/** 收录/更新一条题（按 question 去重 upsert）。 */
export function saveStudyNote(payload) {
  return request('/study/notes', {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  })
}

/** 删除（软删）一条题。 */
export function deleteStudyNote(id) {
  return request(`/study/notes/${id}`, { method: 'DELETE' })
}
