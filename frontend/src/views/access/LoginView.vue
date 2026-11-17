<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { currentUser, errorText, post, type User } from '../../api/access'
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const form = reactive({ account: '', password: '', nickname: '' })
const busy = ref(false), error = ref(''), notice = ref('')
async function submit() {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    if (mode.value === 'register') {
      await post('/accounts', { ...form }); mode.value = 'login'; form.password = ''; notice.value = '注册成功，请使用刚才的账号和密码登录。'
    } else {
      currentUser.value = (await post<User>('/sessions', { account: form.account, password: form.password })).data
      form.password = ''; await router.push('/access')
    }
  } catch (e) { error.value = errorText(e) }
  finally { busy.value = false }
}
function changeMode() { mode.value = mode.value === 'login' ? 'register' : 'login'; form.password = ''; error.value = ''; notice.value = '' }
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">账户与访问</p><h1>{{ mode === 'login' ? '登录你的账户' : '创建新账户' }}</h1><p class="lead">登录后可管理个人资料并申请志愿者资格。</p></div></section>
  <section class="form-panel auth-panel">
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p><p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <form @submit.prevent="submit">
      <label>账号<input v-model="form.account" name="account" required minlength="6" maxlength="20" pattern="[A-Za-z0-9]{6,20}" autocomplete="username" placeholder="6至20位字母或数字" /></label>
      <label v-if="mode === 'register'">昵称<input v-model="form.nickname" name="nickname" required minlength="2" maxlength="20" autocomplete="nickname" /></label>
      <label>密码<input v-model="form.password" name="password" type="password" required :minlength="mode === 'register' ? 8 : 1" :maxlength="mode === 'register' ? 32 : 128" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" /></label>
      <p class="field-help">{{ mode === 'register' ? '密码为8至32位，至少包含一个字母和一个数字。' : '连续5次登录失败后暂停登录15分钟。' }}</p>
      <button class="primary-button" :disabled="busy" type="submit">{{ busy ? '正在处理…' : mode === 'login' ? '登录' : '注册' }}</button>
    </form>
    <button class="link-button" :disabled="busy" @click="changeMode">{{ mode === 'login' ? '还没有账户？立即注册' : '已有账户？返回登录' }}</button>
  </section>
</template>
