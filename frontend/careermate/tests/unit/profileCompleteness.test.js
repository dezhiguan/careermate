import { describe, expect, it } from 'vitest'
import {
  PROFILE_FIELDS,
  computeProfileCompleteness,
  missingProfileFields,
} from '../../src/utils/profileCompleteness'

describe('profileCompleteness', () => {
  it('weights sum to 100', () => {
    expect(PROFILE_FIELDS.reduce((sum, field) => sum + field.weight, 0)).toBe(100)
  })

  it('returns 0 for empty profile', () => {
    expect(computeProfileCompleteness(null)).toBe(0)
    expect(computeProfileCompleteness({})).toBe(0)
  })

  it('counts targetRole as 20', () => {
    expect(computeProfileCompleteness({ targetRole: 'Java 后端' })).toBe(20)
  })

  it('returns 100 when all fields are present', () => {
    expect(computeProfileCompleteness({
      targetRole: 'Java 后端',
      targetCity: '广州',
      seniority: '3-5年',
      workMode: '全职到岗',
      skillKeywords: ['Java'],
    })).toBe(100)
  })

  it('does not count empty skillKeywords', () => {
    const profile = {
      targetRole: 'Java 后端',
      targetCity: '广州',
      seniority: '3-5年',
      workMode: '全职到岗',
      skillKeywords: [],
    }

    expect(computeProfileCompleteness(profile)).toBe(80)
    expect(missingProfileFields(profile)).toEqual(['skillKeywords'])
  })
})
