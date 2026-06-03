/** Agent 内部工具展示名与跳转（路由与 router/index.js 一致） */
export const AGENT_TOOL_META = {
  get_default_resume: {
    label: '默认简历',
    route: '/resume',
    actionLabel: '查看简历',
  },
  get_latest_job_match: {
    label: '最近岗位匹配',
    route: '/match',
    actionLabel: '查看匹配',
  },
  create_job_match: {
    label: '岗位匹配分析',
    route: '/match',
    actionLabel: '查看匹配',
  },
  create_interview_session: {
    label: '面试训练',
    route: '/interview',
    actionLabel: '查看训练',
  },
  get_dashboard_overview: {
    label: '求职概览',
    route: '/dashboard',
    actionLabel: '查看概览',
  },
}

const BUSINESS_TOOL_NAMES = new Set(Object.keys(AGENT_TOOL_META))

export function getToolLabel(toolName) {
  return AGENT_TOOL_META[toolName]?.label || toolName || '工具'
}

export function getToolRoute(toolName) {
  return AGENT_TOOL_META[toolName]?.route || null
}

export function getToolActionLabel(toolName) {
  return AGENT_TOOL_META[toolName]?.actionLabel || '查看详情'
}

export function isBusinessToolName(toolName) {
  return BUSINESS_TOOL_NAMES.has(toolName)
}

/** 仅展示 SSE 摘要，避免把过长文本渲染到卡片 */
export function sanitizeToolSummary(text, maxLen = 240) {
  if (text == null) return ''
  const s = String(text).trim()
  if (s.length <= maxLen) return s
  return `${s.slice(0, maxLen)}…`
}
