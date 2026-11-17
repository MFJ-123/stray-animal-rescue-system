<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { request, createRequestKey } from '../../api/client'
import { refreshMe, hasRole, errorText, post } from '../../api/access'
import { actionLabel, eventState, displayTime, type EventMonitor, type EventSummary } from '../../api/operations'
const monitor = ref<EventMonitor | null>(null), selected = ref<EventSummary | null>(null), status = ref(''), page = ref(1)
const error = ref(''), notice = ref(''), busy = ref(false), allowed = ref(false), reason = ref(''), key = ref(createRequestKey())
watch([reason, () => selected.value?.id], () => { key.value = createRequestKey() })
async function action(work: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; notice.value = ''; try { await work() } catch (e) { error.value = errorText(e) } finally { busy.value = false } }
async function load() { monitor.value = (await request<EventMonitor>(`/outbox-events?${new URLSearchParams({ status: status.value, page: String(page.value) })}`)).data }
const search = () => action(async () => { page.value = 1; selected.value = null; await load() })
const next = (delta: number) => action(async () => { page.value += delta; await load() })
const retry = () => action(async () => { await post(`/outbox-events/${selected.value!.id}/retry`, { reason: reason.value, requestKey: key.value }); selected.value = null; reason.value = ''; key.value = createRequestKey(); await load(); notice.value = '已登记重试，将由已接入的处理服务继续执行。' })
onMounted(() => action(async () => { await refreshMe(); allowed.value = hasRole('SYSTEM_ADMIN'); if (!allowed.value) { error.value = '事件监控仅向系统管理员开放。'; return } await load() }))
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">运营管理</p><h1>事件投递监控</h1><p class="lead">查看待处理事件和失败重试情况，业务结果可独立查询。</p></div></section>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="notice" class="success-message" role="status">{{ notice }}</p>
  <template v-if="allowed && monitor"><section class="form-panel"><h2>处理概况</h2><div class="event-counts"><div v-for="state in ['PENDING','PROCESSING','RETRY','DEAD','DONE']" :key="state"><strong>{{ monitor.counts[state] || 0 }}</strong><span>{{ eventState(state) }}</span></div></div><p class="field-help">最早未完成事件：{{ displayTime(monitor.oldestPendingAt) }}</p><p v-if="!monitor.registeredConsumers.length" class="field-help">尚未接入消息处理服务，待处理事件会继续保留，接入后自动处理。</p><p v-else class="field-help">已接入：{{ monitor.registeredConsumers.map(actionLabel).join('、') }}。其他类型的事件继续等待对应服务。</p></section>
    <section v-if="selected" class="form-panel"><h2>重试事件 #{{ selected.id }}</h2><form @submit.prevent="retry"><label>重试原因<textarea v-model="reason" required minlength="10" maxlength="500" rows="3" /></label><p class="field-help">确认故障原因已处理后登记重试。已完成或正在处理的事件不能重复登记。</p><button class="primary-button" :disabled="busy">登记重试</button><button type="button" class="secondary-button" :disabled="busy" @click="selected = null">取消</button></form></section>
    <section class="form-panel"><form class="search-form" @submit.prevent="search"><label>处理状态<select v-model="status"><option value="">全部状态</option><option v-for="s in ['PENDING','PROCESSING','RETRY','DEAD','DONE']" :key="s" :value="s">{{ eventState(s) }}</option></select></label><button class="primary-button" :disabled="busy">刷新事件</button></form><p class="field-help">共 {{ monitor.events.total }} 条；页面仅展示处理摘要。</p><p v-if="!monitor.events.items.length" class="field-help">当前条件下暂无事件。</p><article v-for="item in monitor.events.items" :key="item.id" class="history-item"><div><strong>#{{ item.id }} · {{ actionLabel(item.eventType) }}</strong><span class="module-state">{{ eventState(item.status) }}</span></div><p>{{ item.objectType }} #{{ item.objectId }} · 已领取 {{ item.attempts }} 次</p><small>登记：{{ displayTime(item.createdAt) }} · {{ item.status === 'PROCESSING' ? '处理超时界限' : '最早可处理' }}：{{ displayTime(item.leaseUntil || item.availableAt) }}</small><p v-if="item.lastError">上次处理失败，请检查对应服务后重试。</p><div v-if="['RETRY','DEAD'].includes(item.status)"><button class="secondary-button" :disabled="busy" @click="selected = item; reason = ''">重试事件 #{{ item.id }}</button></div></article><div class="pagination"><button :disabled="busy || page <= 1" @click="next(-1)">上一页</button><span>第 {{ page }} 页</span><button :disabled="busy || page * 20 >= monitor.events.total" @click="next(1)">下一页</button></div></section>
  </template>
</template>
