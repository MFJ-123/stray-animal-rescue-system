import { createRequestKey, request, type ApiEnvelope } from './client'

/** Create once per user intent. Retry submit() on this object after a network error. */
export function versionedCommand<T>(path: string, payload: Record<string, unknown>, expectedVersion: number,
  method: 'POST' | 'PATCH' | 'PUT' | 'DELETE' = 'POST') {
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('版本号必须为非负安全整数')
  if ('requestKey' in payload || 'expectedVersion' in payload) throw new Error('请求键和版本号由公共命令工具提供')
  const requestKey = createRequestKey()
  // Serialize immediately: editing the form cannot alter a pending command or its retry.
  const body = JSON.stringify({ ...payload, expectedVersion, requestKey })
  let pending: Promise<ApiEnvelope<T>> | undefined
  return Object.freeze({
    requestKey, expectedVersion,
    submit(): Promise<ApiEnvelope<T>> {
      if (!pending) pending = request<T>(path, { method, body }).finally(() => { pending = undefined })
      return pending
    },
  })
}
