import { describe, expect, it, vi, beforeEach } from 'vitest'

const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

vi.stubGlobal('localStorage', {
  getItem: vi.fn(() => null),
  removeItem: vi.fn(),
})

beforeEach(() => {
  mockFetch.mockReset()
  vi.resetModules()
})

describe('http requestWithMeta', () => {
  it('reads X-Trace-Id and X-Request-Id from response headers', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: (name) => {
          if (name === 'X-Trace-Id') return 'trace-from-header'
          if (name === 'X-Request-Id') return 'req-from-header'
          return null
        },
      },
      json: async () => ({ code: 0, data: { ok: true } }),
    })

    const { requestWithMeta, getLastTraceMeta } = await import('../../src/api/http.js')
    const result = await requestWithMeta('/health')

    expect(result.data).toEqual({ ok: true })
    expect(result.traceId).toBe('trace-from-header')
    expect(result.requestId).toBe('req-from-header')
    expect(getLastTraceMeta()).toEqual({
      traceId: 'trace-from-header',
      requestId: 'req-from-header',
    })
  })

  it('request() still returns data only', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: { get: () => null },
      json: async () => ({ code: 0, data: [1, 2, 3] }),
    })

    const { request } = await import('../../src/api/http.js')
    await expect(request('/items')).resolves.toEqual([1, 2, 3])
  })
})
