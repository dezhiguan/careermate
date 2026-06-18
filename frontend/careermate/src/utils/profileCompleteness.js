// 单一来源：画像完整度算分。AgentChat banner 与 MineView 显示必须用同一份。
export const PROFILE_FIELDS = [
  { key: 'targetRole', weight: 20, check: (p) => !!p?.targetRole?.trim() },
  { key: 'targetCity', weight: 20, check: (p) => !!p?.targetCity?.trim() },
  { key: 'seniority', weight: 20, check: (p) => !!p?.seniority?.trim() },
  { key: 'workMode', weight: 20, check: (p) => !!p?.workMode?.trim() },
  { key: 'skillKeywords', weight: 20, check: (p) => Array.isArray(p?.skillKeywords) && p.skillKeywords.length > 0 },
]

export function computeProfileCompleteness(profile) {
  if (!profile) return 0
  return PROFILE_FIELDS.reduce((acc, f) => acc + (f.check(profile) ? f.weight : 0), 0)
}

export function missingProfileFields(profile) {
  return PROFILE_FIELDS.filter((f) => !f.check(profile)).map((f) => f.key)
}
