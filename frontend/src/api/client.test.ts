import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './client'
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
function stubApi(fetcher: (url: string, options: RequestInit) => Promise<Response>) {
  vi.stubGlobal('fetch', (url: string, options: RequestInit) => url === '/api/v1/csrf'
    ? Promise.resolve(new Response(JSON.stringify({ code: 'OK', data: { token: 'test-csrf-token' } })))
    : fetcher(url, options))
}
describe('shared API client', () => {
  it('bounds CSRF bootstrap by the same timeout and never sends the business command after it hangs', async () => {
    vi.useFakeTimers()
    const fetcher = vi.fn((_url: string, options: RequestInit) => new Promise<Response>((_resolve, reject) => {
      options.signal!.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }))
    vi.stubGlobal('fetch', fetcher)
    const result = expect(request('/example', { method: 'POST', body: '{}' })).rejects.toMatchObject({ code: 'NETWORK_ERROR', message: expect.stringContaining('超时') })
    await vi.advanceTimersByTimeAsync(10001)
    await result
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher.mock.calls[0][0]).toBe('/api/v1/csrf')
  })
  it('propagates external cancellation and expires the shared login state on 401', async () => {
    const controller = new AbortController()
    vi.stubGlobal('fetch', (_url: string, options: RequestInit) => new Promise((_resolve, reject) => {
      options.signal!.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
    }))
    const cancelled = expect(request('/example', { signal: controller.signal })).rejects.toMatchObject({ code: 'REQUEST_CANCELLED' })
    controller.abort(); await cancelled
    const dispatchEvent = vi.fn()
    vi.stubGlobal('window', { dispatchEvent })
    stubApi(async () => new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: '登录失效', requestId: 'auth', data: null }), { status: 401 }))
    await expect(request('/example')).rejects.toMatchObject({ status: 401 })
    expect(dispatchEvent).toHaveBeenCalledOnce()
    expect(dispatchEvent.mock.calls[0][0].type).toBe('sar:session-expired')
  })
  it('uses same-origin credentials and preserves caller request body', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'OK', message: '成功', requestId: 'r1', data: { id: '1' } })))
    stubApi(fetcher)
    const payload = JSON.stringify({ requestKey: 'same-key', expectedVersion: 1 })
    expect((await request<{ id: string }>('/example', { method: 'POST', body: payload })).data.id).toBe('1')
    expect(fetcher).toHaveBeenCalledWith('/api/v1/example', expect.objectContaining({ credentials: 'same-origin', body: payload }))
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('preserves conflict details and request ID without retrying a write', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: '请刷新', requestId: 'r2', data: { version: 2 } }), { status: 409 }))
    stubApi(fetcher)
    await expect(request('/example', { method: 'POST', body: '{}' })).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT', requestId: 'r2', data: { version: 2 } })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('reports unavailable backend and invalid response formats', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('/system/status')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>proxy failure</html>', { status: 502 })))
    await expect(request('/system/status')).rejects.toBeInstanceOf(ApiError)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}')))
    await expect(request('/system/status')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('does not manually set multipart boundary', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'OK', message: '', requestId: 'r3', data: null })))
    stubApi(fetcher)
    await request('/attachments', { method: 'POST', body: new FormData() })
    expect((fetcher.mock.calls[0][1].headers as Headers).has('Content-Type')).toBe(false)
  })
})
