export interface ApiEnvelope<T> { code: string; message: string; requestId: string; data: T }
export class ApiError extends Error {
  constructor(message: string, public status: number, public code: string, public requestId: string, public data: unknown = null) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<ApiEnvelope<T>> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('接口路径必须为 / 开头的站内路径')
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes((options.method || 'GET').toUpperCase())) {
    try {
      const csrf = await fetch('/api/v1/csrf', { credentials: 'same-origin', cache: 'no-store' })
      const envelope = await csrf.json()
      if (!csrf.ok || typeof envelope?.data?.token !== 'string') throw new Error('csrf unavailable')
      headers.set('X-XSRF-TOKEN', envelope.data.token)
    } catch { throw new ApiError('页面验证暂不可用，请刷新后重试', 0, 'CSRF_UNAVAILABLE', '') }
  }
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const controller = new AbortController()
  const signal = options.signal ? AbortSignal.any([options.signal, controller.signal]) : controller.signal
  const timeout = setTimeout(() => controller.abort(), 10000)
  try {
    const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'same-origin', signal })
    const requestId = response.headers.get('X-Request-Id') || ''
    let body: ApiEnvelope<T>
    try { body = await response.json() as ApiEnvelope<T> }
    catch { throw new ApiError('服务返回格式异常，请稍后重试', response.status, 'INVALID_RESPONSE', requestId) }
    if (!body || typeof body.code !== 'string' || typeof body.message !== 'string' || typeof body.requestId !== 'string' || !('data' in body)) {
      throw new ApiError('服务返回格式异常，请稍后重试', response.status, 'INVALID_RESPONSE', requestId)
    }
    if (!response.ok || body.code !== 'OK') {
      if (response.status === 401 && typeof window !== 'undefined') window.dispatchEvent(new Event('sar:session-expired'))
      throw new ApiError(body.message, response.status, body.code, body.requestId || requestId, body.data)
    }
    return body
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(controller.signal.aborted ? '请求超时，请检查连接后重试' : '无法连接服务，请确认后端已启动', 0, 'NETWORK_ERROR', '')
  } finally { clearTimeout(timeout) }
}

// 一次新操作生成一次键；网络失败重试时由调用页面复用同一个键，公共客户端不自动重放写请求。
export const createRequestKey = () => crypto.randomUUID()
