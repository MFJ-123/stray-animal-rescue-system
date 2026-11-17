<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request, createRequestKey } from '../../api/client'
import { currentUser, refreshMe, post, roleLabel, stateLabel, errorText, type User, type Application, type Page } from '../../api/access'
const router = useRouter()
const profile = reactive({ nickname: '', contact: '' }), password = reactive({ oldPassword: '', newPassword: '' })
const applications = ref<Application[]>([]), total = ref(0), page = ref(1), material = ref(''), key = ref(createRequestKey())
const busy = ref(false), error = ref(''), notice = ref('')
async function load() {
  const user = await refreshMe(); if (!user) { await router.push('/login'); return }
  profile.nickname = user.nickname; profile.contact = user.contact
  const result = (await request<Page<Application>>(`/volunteer-requests?page=${page.value}`)).data
  applications.value = result.items; total.value = result.total
}
async function action(work: () => Promise<void>) {
  busy.value = true; error.value = ''; notice.value = ''
  try { await work() } catch (e) { error.value = errorText(e) } finally { busy.value = false }
}
const saveProfile = () => action(async () => { currentUser.value = (await request<User>('/accounts/me', { method: 'PATCH', body: JSON.stringify({ ...profile, expectedVersion: currentUser.value!.version }) })).data; notice.value = '个人资料已保存。' })
const changePassword = () => action(async () => { await post('/accounts/me/password', { ...password, expectedVersion: currentUser.value!.version }); password.oldPassword = ''; password.newPassword = ''; currentUser.value = null; await router.push('/login') })
const apply = () => action(async () => { await post('/volunteer-requests', { material: material.value, requestKey: key.value }); material.value = ''; key.value = createRequestKey(); notice.value = '申请已提交，可在下方查看审核进度。'; await load() })
async function next(delta: number) { page.value += delta; await action(load) }
onMounted(() => action(load))
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">账户与访问</p><h1>个人账户</h1><p v-if="currentUser" class="lead">{{ currentUser.account }} · {{ currentUser.roles.map(roleLabel).join('、') }}</p></div></section>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="notice" class="success-message" role="status">{{ notice }}</p>
  <div v-if="currentUser" class="account-grid">
    <section class="form-panel"><h2>个人资料</h2><form @submit.prevent="saveProfile"><label>昵称<input v-model="profile.nickname" required minlength="2" maxlength="20" autocomplete="nickname" /></label><label>联系方式<input v-model="profile.contact" maxlength="100" autocomplete="tel" placeholder="仅用于本人资料管理" /></label><button class="primary-button" :disabled="busy">保存资料</button></form></section>
    <section class="form-panel"><h2>修改密码</h2><p class="field-help">修改成功后，所有设备上的旧会话都会失效，请重新登录。</p><form @submit.prevent="changePassword"><label>原密码<input v-model="password.oldPassword" type="password" required maxlength="128" autocomplete="current-password" /></label><label>新密码<input v-model="password.newPassword" type="password" required minlength="8" maxlength="32" autocomplete="new-password" /></label><button class="primary-button" :disabled="busy">修改密码并退出</button></form></section>
  </div>
  <section v-if="currentUser" class="form-panel"><h2>志愿者资格 <span class="module-state">{{ stateLabel(currentUser.volunteerState) }}</span></h2>
    <form v-if="currentUser.volunteerState === 'NONE' && !applications.some(a => a.state === 'PENDING')" @submit.prevent="apply"><label>申请说明<textarea v-model="material" required minlength="10" maxlength="500" rows="4" placeholder="说明参与救助的意愿和可提供的协助，10至500字" @input="key = createRequestKey()"></textarea></label><button class="primary-button" :disabled="busy">提交志愿者申请</button></form>
    <p v-else class="field-help">{{ currentUser.volunteerState === 'ACTIVE' ? '你已具有有效志愿者资格。' : currentUser.volunteerState === 'SUSPENDED' ? '资格已暂停，请联系救助管理员了解后续安排。' : '已有申请等待审核，请勿重复提交。' }}</p>
    <h3 class="subheading">申请记录</h3><p v-if="!applications.length" class="field-help">暂无申请记录。</p>
    <article v-for="item in applications" :key="item.id" class="history-item"><div><strong>申请 #{{ item.id }}</strong><span class="module-state">{{ stateLabel(item.state) }}</span></div><p class="preserve-text">{{ item.material }}</p><p v-if="item.reason">审核意见：{{ item.reason }}</p><small>{{ new Date(item.createdAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</small></article>
    <div class="pagination"><button :disabled="busy || page <= 1" @click="next(-1)">上一页</button><span>第 {{ page }} 页 · 共 {{ total }} 条</span><button :disabled="busy || page * 20 >= total" @click="next(1)">下一页</button></div>
  </section>
</template>
