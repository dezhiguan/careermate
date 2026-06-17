import { describe, expect, it } from 'vitest'

const CONFIRM_ACTION_CARD = {
  type: 'CONFIRM_ACTION',
  title: '确认按 JD 生成定制简历',
  summary: '将基于当前工作空间中的 JD 生成新的简历版本并保存。',
  riskLabel: '高风险写入',
  actionId: 'PA-demo',
  expiresAt: '2026-06-17T22:00:00+08:00',
  actions: [
    { label: '确认生成', action: 'CONFIRM_PENDING_ACTION', payload: { actionId: 'PA-demo' } },
    { label: '取消', action: 'CANCEL_PENDING_ACTION', payload: { actionId: 'PA-demo' } },
  ],
}

describe('CONFIRM_ACTION card contract', () => {
  it('includes title summary risk and dual actions', () => {
    expect(CONFIRM_ACTION_CARD.type).toBe('CONFIRM_ACTION')
    expect(CONFIRM_ACTION_CARD.title).toBeTruthy()
    expect(CONFIRM_ACTION_CARD.summary).toBeTruthy()
    expect(CONFIRM_ACTION_CARD.riskLabel).toBe('高风险写入')
    expect(CONFIRM_ACTION_CARD.actions).toHaveLength(2)
    expect(CONFIRM_ACTION_CARD.actions[0].action).toBe('CONFIRM_PENDING_ACTION')
    expect(CONFIRM_ACTION_CARD.actions[1].action).toBe('CANCEL_PENDING_ACTION')
    expect(CONFIRM_ACTION_CARD.actions[0].payload.actionId).toBe('PA-demo')
  })

  it('does not include sensitive jd or resume content fields', () => {
    expect(CONFIRM_ACTION_CARD).not.toHaveProperty('jdContent')
    expect(CONFIRM_ACTION_CARD).not.toHaveProperty('resumeContent')
    expect(CONFIRM_ACTION_CARD).not.toHaveProperty('content')
  })
})
