export interface Audit { id: string; actorId: string; module: string; objectType: string; objectId: string; action: string; beforeState: string; afterState: string; reason: string; requestId: string; createdAt: string; correctsId: string | null }
export interface AuditDetail { record: Audit; corrections: Audit[] }
export interface EventSummary { id: string; eventType: string; objectType: string; objectId: string; status: string; attempts: number; availableAt: string; leaseUntil: string | null; lastError: string; createdAt: string }
export interface EventMonitor { events: { items: EventSummary[]; total: number; page: number; pageSize: number }; counts: Record<string, number>; oldestPendingAt: string | null; registeredConsumers: string[] }
export const moduleLabel = (v: string) => ({ ACCESS: '账户与资格', RESCUE: '线索与救助', ANIMALCARE: '动物与照护', ADOPTION: '领养申请', FOLLOWUP: '退养与回访', OPERATIONS: '运营管理' }[v] || v)
export const actionLabel = (v: string) => ({ ADMIN_BOOTSTRAPPED: '初始化管理员', ROLES_CHANGED: '管理角色变更', PASSWORD_CHANGED: '修改密码', VOLUNTEER_APPLIED: '提交资格申请', VOLUNTEER_REVIEWED: '审核资格申请', VOLUNTEER_SUSPENDED: '暂停志愿者资格', AUDIT_CORRECTION: '追加纠错说明', EVENT_RETRY_REQUESTED: '申请事件重试', ACCESS_CHANGED: '账户或资格变化', AUDIT_CORRECTED: '审计追加纠错' }[v] || v)
export const eventState = (v: string) => ({ PENDING: '待处理', PROCESSING: '处理中', RETRY: '等待重试', DEAD: '需要人工处理', DONE: '已完成' }[v] || v)
export const displayTime = (v: string | null) => v ? new Date(v).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) : '暂无'
