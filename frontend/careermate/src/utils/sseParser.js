export function createSseParser({ onEvent, onError }) {
  let buffer = ''

  function flushEventBlock(block) {
    if (!block || !block.trim()) return
    const lines = block.split('\n')
    let eventName = 'message'
    const dataLines = []

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim() || 'message'
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }

    const rawData = dataLines.join('\n')
    let data = rawData
    if (rawData) {
      try {
        data = JSON.parse(rawData)
      } catch (e) {
        onError?.(new Error(`SSE data parse error: ${e.message}`))
      }
    }

    onEvent?.({ eventName, data })
  }

  return {
    push(chunkText) {
      if (!chunkText) return
      buffer += chunkText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const block = buffer.slice(0, boundary)
        flushEventBlock(block)
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    },
    end() {
      if (buffer.trim()) {
        flushEventBlock(buffer)
      }
      buffer = ''
    },
  }
}
