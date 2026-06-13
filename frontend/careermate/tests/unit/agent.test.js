import { describe, expect, it, vi, beforeEach } from 'vitest'

const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

vi.stubGlobal('localStorage', {
  getItem: vi.fn(() => 'token'),
  removeItem: vi.fn(),
})

vi.stubGlobal('window', {
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (id) => clearTimeout(id),
})

beforeEach(() => {
  mockFetch.mockReset()
  vi.resetModules()
})

describe('sendAgentMessageStream onTraceHeader', () => {
  it('invokes onTraceHeader with response headers before reading SSE body', async () => {
    const encoder = new TextEncoder()
    let readCount = 0
    const onTraceHeader = vi.fn()

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: {
        get: (name) => {
          if (name === 'X-Trace-Id') return 'sse-trace-id'
          if (name === 'X-Request-Id') return 'sse-req-id'
          return null
        },
      },
      body: {
        getReader() {
          return {
            async read() {
              readCount += 1
              if (readCount === 1) {
                return { value: encoder.encode('event: done\ndata: {"type":"done"}\n\n'), done: false }
              }
              return { done: true }
            },
            releaseLock() {},
            cancel() {},
          }
        },
      },
    })

    const { sendAgentMessageStream } = await import('../../src/api/agent.js')
    await sendAgentMessageStream('session-1', 'hello', { onTraceHeader })

    expect(onTraceHeader).toHaveBeenCalledWith({
      traceId: 'sse-trace-id',
      requestId: 'sse-req-id',
    })
  })
})
