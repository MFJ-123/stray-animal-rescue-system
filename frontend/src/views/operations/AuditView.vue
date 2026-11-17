<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { request, createRequestKey } from '../../api/client'
import { refreshMe, hasRole, errorText, post, type Page } from '../../api/access'
import { moduleLabel, actionLabel, displayTime, type Audit, type AuditDetail } from '../../api/operations'
const filters = reactive({ module: '', objectType: '', objectId: '', requestId: '', from: '', to: '' })
const items = ref<Audit[]>([]), total = ref(0), page = ref(1), detail = ref<AuditDetail | null>(null)
const busy = ref(false), error = ref(''), notice = ref(''), reason = ref(''), key = ref(createRequestKey()), allowed = ref(false)
watch([reason, () => detail.value?.record.id], () => { key.value = createRequestKey() })
async function action(work: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; notice.value = ''; try { await work() } catch (e) { error.value = errorText(e) } finally { busy.value = false } }
async function load() {
  const params = new URLSearchParams({ page: String(page.value), pageSize: '20' })
  for (const [name, value] of Object.entries(filters)) if (value) params.set(name, name === 'from' || name === 'to' ? new Date(value).toISOString() : value)
  const data = (await request<Page<Audit>>(`/audit-logs?${params}`)).data; items.value = data.items; total.value = data.total
}
const search = () => action(async () => { page.value = 1; detail.value = null; await load() })
const select = (id: string) => action(async () => { detail.value = (await request<AuditDetail>(`/audit-logs/${id}`)).data; reason.value = '' })
const correct = () => action(async () => {
  const id = detail.value!.record.id
  await post(`/audit-logs/${id}/corrections`, { reason: reason.value, requestKey: key.value })
  detail.value = (await request<AuditDetail>(`/audit-logs/${id}`)).data; reason.value = ''; key.value = createRequestKey(); await load(); notice.value = '纠错说明已追加，原始记录继续保留。'
})
const next = (delta: number) => action(async () => { page.value += delta; await load() })
onMounted(() => action(async () => { await refreshMe(); allowed.value = ['SYSTEM_ADMIN', 'RESCUE_ADMIN', 'ADOPTION_ADMIN'].some(hasRole); if (!allowed.value) { error.value = '当前账户没有审计查询权限，请使用相应管理账户登录。'; return } await load() }))
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">运营管理</p><h1>操作审计</h1><p class="lead">按对象、时间和请求号追查变更，在原记录下保留纠错依据。</p></div></section>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="notice" class="success-message" role="status">{{ notice }}</p>
  <template v-if="allowed">
    <section class="form-panel"><h2>查询条件</h2><p class="field-help">仅显示当前管理职责范围内的摘要。时间范围包含开始时刻，不包含结束时刻。</p>
      <form class="audit-filters" @submit.prevent="search"><label>业务范围<select v-model="filters.module"><option value="">全部可见范围</option><option v-for="m in ['ACCESS','RESCUE','ANIMALCARE','ADOPTION','FOLLOWUP','OPERATIONS']" :key="m" :value="m">{{ moduleLabel(m) }}</option></select></label><label>对象类型<input v-model="filters.objectType" maxlength="32" placeholder="如 ACCOUNT" /></label><label>对象编号<input v-model="filters.objectId" inputmode="numeric" pattern="[1-9][0-9]*" /></label><label>请求号<input v-model="filters.requestId" maxlength="64" /></label><label>开始时间<input v-model="filters.from" type="datetime-local" /></label><label>结束时间<input v-model="filters.to" type="datetime-local" /></label><button class="primary-button" :disabled="busy">查询审计</button></form>
    </section>
    <section v-if="detail" class="form-panel" aria-label="审计详情"><h2>原始记录 #{{ detail.record.id }} · {{ actionLabel(detail.record.action) }}</h2><p class="field-help">{{ moduleLabel(detail.record.module) }} · {{ detail.record.objectType }} #{{ detail.record.objectId }} · 操作者 #{{ detail.record.actorId }} · {{ displayTime(detail.record.createdAt) }}</p><p class="preserve-text">{{ detail.record.beforeState || '无前态' }} → {{ detail.record.afterState || '无后态' }}</p><p class="preserve-text audit-reason">{{ detail.record.reason }}</p><p class="field-help preserve-text">请求号：{{ detail.record.requestId }}</p>
      <h3>关联纠错说明（{{ detail.corrections.length }}）</h3><article v-for="note in detail.corrections" :key="note.id" class="history-item correction-item"><p class="preserve-text">{{ note.reason }}</p><small>记录 #{{ note.id }} · 操作者 #{{ note.actorId }} · {{ displayTime(note.createdAt) }}</small></article><p v-if="!detail.corrections.length" class="field-help">暂无关联纠错说明。</p>
      <form @submit.prevent="correct"><label>纠错说明<textarea v-model="reason" required minlength="10" maxlength="500" rows="3" /></label><p class="field-help">说明依据和处理结果，10至500字。填写摘要，避免复制联系方式、凭据和申请材料；涉及业务状态更正时应由对应业务流程办理。</p><button class="primary-button" :disabled="busy">追加纠错说明</button></form>
    </section>
    <section class="form-panel"><h2>查询结果 <span class="module-state">共 {{ total }} 条</span></h2><p v-if="!items.length" class="field-help">当前条件下暂无可见记录。</p><article v-for="item in items" :key="item.id" class="history-item audit-item"><div><strong>#{{ item.id }} · {{ actionLabel(item.action) }}</strong><span class="module-state">{{ moduleLabel(item.module) }}</span></div><p>{{ item.objectType }} #{{ item.objectId }} · 操作者 #{{ item.actorId }}</p><p class="preserve-text">{{ item.reason }}</p><small>{{ displayTime(item.createdAt) }} · {{ item.correctsId ? `关联原记录 #${item.correctsId}` : '原始变更' }}</small><div><button class="secondary-button" :disabled="busy" @click="select(item.id)">查看记录 #{{ item.id }}</button></div></article><div class="pagination"><button :disabled="busy || page <= 1" @click="next(-1)">上一页</button><span>第 {{ page }} 页</span><button :disabled="busy || page * 20 >= total" @click="next(1)">下一页</button></div></section>
  </template>
</template>
