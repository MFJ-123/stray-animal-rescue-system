<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, request } from '../../api/client'
import { refreshMe, hasRole, post, stateLabel, roleLabel, errorText, type User, type Page, type Application, type ResponsibilityReport } from '../../api/access'
const route = useRoute(), router = useRouter()
const kind = computed(() => String(route.meta.manageKind))
const users = ref<User[]>([]), applications = ref<Application[]>([]), selected = ref<User | null>(null), reviewItem = ref<Application | null>(null)
const roles = ref<string[]>([]), reason = ref(''), keyword = ref(''), page = ref(1), total = ref(0), report = ref<ResponsibilityReport | null>(null)
const decision = ref('APPROVED'), busy = ref(false), error = ref(''), notice = ref('')
async function run(work: () => Promise<void>) { busy.value = true; error.value = ''; notice.value = ''; try { await work() } catch (e) { error.value = errorText(e); if (e instanceof ApiError && e.data && typeof e.data === 'object' && 'unavailableModules' in e.data) report.value = e.data as ResponsibilityReport } finally { busy.value = false } }
async function load() {
  const user = await refreshMe(); if (!user) { await router.push('/login'); return }
  if (!hasRole(kind.value === 'roles' ? 'SYSTEM_ADMIN' : 'RESCUE_ADMIN')) { error.value = '当前角色没有此管理权限。'; return }
  if (kind.value === 'review') {
    const data = (await request<Page<Application>>(`/volunteer-requests?review=true&page=${page.value}`)).data; applications.value = data.items; total.value = data.total
  } else {
    const data = (await request<Page<User>>(`/${kind.value === 'roles' ? 'accounts' : 'volunteers'}?page=${page.value}&keyword=${encodeURIComponent(keyword.value)}`)).data; users.value = data.items; total.value = data.total
  }
}
function selectUser(user: User) { selected.value = user; roles.value = user.roles.filter(r => ['SYSTEM_ADMIN', 'RESCUE_ADMIN', 'ADOPTION_ADMIN'].includes(r)); reason.value = ''; report.value = null }
const saveRoles = () => run(async () => { await post(`/accounts/${selected.value!.id}/roles`, { roles: roles.value, reason: reason.value, expectedVersion: selected.value!.version }); selected.value = null; await load(); notice.value = '管理角色已保存，权限将在后续请求中即时生效。' })
const review = () => run(async () => { await post(`/volunteer-requests/${reviewItem.value!.id}/review`, { decision: decision.value, reason: reason.value, expectedVersion: reviewItem.value!.version }); reviewItem.value = null; await load(); notice.value = '审核结果已保存。' })
const checkResponsibilities = () => run(async () => { report.value = (await request<ResponsibilityReport>(`/volunteers/${selected.value!.id}/responsibilities`)).data })
const suspend = () => run(async () => { await post(`/volunteers/${selected.value!.id}/suspend`, { reason: reason.value, expectedVersion: selected.value!.version }); selected.value = null; await load(); notice.value = '资格已暂停，历史记录已保留。' })
async function changePage(delta: number) { page.value += delta; selected.value = null; reviewItem.value = null; await run(load) }
watch(kind, () => { page.value = 1; users.value = []; applications.value = []; selected.value = null; reviewItem.value = null; report.value = null; run(load) })
onMounted(() => run(load))
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">管理工作台</p><h1>{{ route.meta.title }}</h1><p class="lead">{{ kind === 'roles' ? '管理角色与志愿者资格分别管理，配置角色不要求交接责任。' : kind === 'review' ? '审核通过后自动授予志愿者资格，驳回保留普通用户功能。' : '暂停前核实任务、协作、照护及回访责任，处理记录保留。' }}</p></div></section>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="notice" class="success-message" role="status">{{ notice }}</p>
  <section class="form-panel">
    <form v-if="kind !== 'review'" class="search-form" @submit.prevent="page = 1; run(load)"><label>账号或昵称<input v-model="keyword" maxlength="50" /></label><button class="primary-button" :disabled="busy">查询</button></form>
    <div v-if="kind === 'review'" class="record-list"><p v-if="!applications.length">暂无资格申请。</p><article v-for="item in applications" :key="item.id" class="history-item"><div><strong>{{ item.nickname }} · 申请 #{{ item.id }}</strong><span class="module-state">{{ stateLabel(item.state) }}</span></div><p class="preserve-text">{{ item.material }}</p><p v-if="item.reason">意见：{{ item.reason }}</p><button v-if="item.state === 'PENDING'" class="secondary-button" :disabled="busy" @click="reviewItem = item; reason = ''; decision = 'APPROVED'">审核申请</button></article></div>
    <div v-else class="record-list"><p v-if="!users.length">没有匹配的账户。</p><article v-for="user in users" :key="user.id" class="user-row"><div><strong>{{ user.nickname }}</strong><p>{{ user.account }} · {{ stateLabel(user.volunteerState) }}</p><small>{{ user.roles.map(roleLabel).join('、') }}</small></div><button class="secondary-button" :disabled="busy || (kind === 'volunteers' && user.volunteerState !== 'ACTIVE')" @click="selectUser(user)">{{ kind === 'roles' ? '配置角色' : '管理资格' }}</button></article></div>
    <div class="pagination"><button :disabled="busy || page <= 1" @click="changePage(-1)">上一页</button><span>第 {{ page }} 页 · 共 {{ total }} 条</span><button :disabled="busy || page * 20 >= total" @click="changePage(1)">下一页</button></div>
  </section>
  <section v-if="selected" class="form-panel"><h2>{{ selected.nickname }} · {{ kind === 'roles' ? '管理角色配置' : '暂停资格' }}</h2>
    <form v-if="kind === 'roles'" @submit.prevent="saveRoles"><div class="checkboxes"><label v-for="role in ['SYSTEM_ADMIN', 'RESCUE_ADMIN', 'ADOPTION_ADMIN']" :key="role"><input v-model="roles" type="checkbox" :value="role" />{{ roleLabel(role) }}</label></div><label>变更原因<textarea v-model="reason" required minlength="10" maxlength="500" rows="3"></textarea></label><button class="primary-button" :disabled="busy">保存管理角色</button></form>
    <template v-else><button class="secondary-button" :disabled="busy" @click="checkResponsibilities">查询未结责任</button><div v-if="report" class="responsibility-report"><p v-if="report.unavailableModules.length" class="error-message">以下责任模块尚未接通或暂不可用，不能暂停：{{ report.unavailableModules.join('、') }}</p><p v-else-if="!report.openResponsibilities.length" class="success-message">当前没有未结责任，提交时服务端会再次核查。</p><ul v-if="report.openResponsibilities.length"><li v-for="item in report.openResponsibilities" :key="item.type + item.objectId">{{ item.type }} #{{ item.objectId }}：{{ item.description }}</li></ul></div><form @submit.prevent="suspend"><label>暂停原因<textarea v-model="reason" required minlength="10" maxlength="500" rows="3"></textarea></label><button class="primary-button" :disabled="busy || !report || report.unavailableModules.length > 0 || report.openResponsibilities.length > 0">核查并暂停资格</button></form></template>
  </section>
  <section v-if="reviewItem" class="form-panel"><h2>审核申请 #{{ reviewItem.id }}</h2><p class="preserve-text">{{ reviewItem.material }}</p><form @submit.prevent="review"><label>审核决定<select v-model="decision"><option value="APPROVED">通过</option><option value="REJECTED">驳回</option></select></label><label>审核意见<textarea v-model="reason" required minlength="10" maxlength="500" rows="3"></textarea></label><button class="primary-button" :disabled="busy">提交审核</button></form></section>
</template>
