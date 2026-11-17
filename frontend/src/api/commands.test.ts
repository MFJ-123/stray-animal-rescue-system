import { afterEach, expect, it, vi } from 'vitest'
import { versionedCommand } from './commands'

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })
const ok = () => new Response(JSON.stringify({ code: 'OK', message: '', requestId: 'r1', data: { version: 4 } }))
function setup() {
  const business = vi.fn().mockImplementation(async () => ok())
  vi.stubGlobal('fetch', (url: string, options: RequestInit) => url.endsWith('/csrf')
    ? Promise.resolve(new Response(JSON.stringify({ data: { token: 'csrf' } }))) : business(url, options))
  return business
}
it('keeps the same immutable payload/key/version on explicit retry and makes new keys for new intents', async () => {
  const business = setup()
  business.mockRejectedValueOnce(new TypeError('offline'))
  const payload = { nickname: '原昵称', contact: 'original' }
  const command = versionedCommand('/accounts/me', payload, 3, 'PATCH')
  payload.contact = 'edited later'
  await expect(command.submit()).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  await command.submit()
  const first = business.mock.calls[0][1].body
  expect(business.mock.calls[1][1].body).toBe(first)
  expect(JSON.parse(first)).toMatchObject({ nickname: '原昵称', contact: 'original', expectedVersion: 3, requestKey: command.requestKey })
  expect(versionedCommand('/accounts/me', payload, 3).requestKey).not.toBe(command.requestKey)
})
it('coalesces simultaneous submits but does not automatically retry conflicts', async () => {
  const business = setup()
  business.mockResolvedValueOnce(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: '刷新', requestId: 'conflict', data: { currentVersion: 5 } }), { status: 409 }))
  const command = versionedCommand('/accounts/me', {}, 3)
  const first = command.submit(), second = command.submit()
  expect(second).toBe(first)
  await expect(first).rejects.toMatchObject({ status: 409, requestId: 'conflict', data: { currentVersion: 5 } })
  expect(business).toHaveBeenCalledTimes(1)
})
it('rejects invalid versions and reserved command fields', () => {
  expect(() => versionedCommand('/x', {}, -1)).toThrow()
  expect(() => versionedCommand('/x', {}, Number.MAX_SAFE_INTEGER + 1)).toThrow()
  expect(() => versionedCommand('/x', { requestKey: 'client-overwrite' }, 0)).toThrow()
})
