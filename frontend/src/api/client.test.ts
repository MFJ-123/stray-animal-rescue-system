import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './client'
afterEach(() => vi.unstubAllGlobals())
describe('shared API client', () => {
  it('uses same-origin credentials and preserves caller request body', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'OK', message: '成功', requestId: 'r1', data: { id: '1' } })))
    vi.stubGlobal('fetch', fetcher)
    const payload = JSON.stringify({ requestKey: 'same-key', expectedVersion: 1 })
    expect((await request<{ id: string }>('/example', { method: 'POST', body: payload })).data.id).toBe('1')
    expect(fetcher).toHaveBeenCalledWith('/api/v1/example', expect.objectContaining({ credentials: 'same-origin', body: payload }))
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('preserves conflict details and request ID without retrying a write', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: '请刷新', requestId: 'r2', data: { version: 2 } }), { status: 409 }))
    vi.stubGlobal('fetch', fetcher)
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
    vi.stubGlobal('fetch', fetcher)
    await request('/attachments', { method: 'POST', body: new FormData() })
    expect((fetcher.mock.calls[0][1].headers as Headers).has('Content-Type')).toBe(false)
  })
})
