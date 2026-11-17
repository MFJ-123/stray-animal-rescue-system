<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '../api/client'
import { getSystemStatus, type SystemStatus } from '../api/system'
import { businessModules } from '../router/modules/business'
const loading = ref(false)
const status = ref<SystemStatus | null>(null)
const error = ref('')
const requestId = ref('')
async function checkConnection() {
  loading.value = true
  status.value = null
  error.value = ''
  requestId.value = ''
  try {
    const result = await getSystemStatus()
    status.value = result.data
    requestId.value = result.requestId
  } catch (e) {
    error.value = e instanceof Error ? e.message : '连接检查失败'
    requestId.value = e instanceof ApiError ? e.requestId : ''
  } finally { loading.value = false }
}
onMounted(checkConnection)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">项目工作台</p><h1>流浪动物救助系统</h1><p class="lead">从线索上报到领养回访，在同一个工作空间协作。</p></div><span class="version-label">基础版本 0.1.0</span></section>
  <section class="connection-panel" aria-labelledby="connection-title">
    <div><div class="section-heading"><span class="status-dot" :class="status ? 'connected' : error ? 'failed' : ''"></span><h2 id="connection-title">服务连接</h2></div>
      <p class="connection-message" role="status" aria-live="polite">{{ loading ? '正在检查后端与数据库…' : error || (status ? '后端与数据库已连接，基础工程运行正常。' : '尚未检查连接。') }}</p>
      <p v-if="status" class="connection-detail">MySQL {{ status.databaseVersion }}<span>基础数据版本 {{ status.foundationVersion }}</span></p>
      <p v-if="requestId" class="request-id">请求号：{{ requestId }}</p>
    </div>
    <button class="primary-button" :disabled="loading" @click="checkConnection">{{ loading ? '检查中…' : '重新检查' }}<span aria-hidden="true">↗</span></button>
  </section>
  <section aria-labelledby="modules-title"><div class="section-title-row"><h2 id="modules-title">业务模块</h2><span class="muted">7 个模块 · 5 人协作</span></div>
    <p class="section-description">账户与资格、审计和事件监控已接入，其他业务功能等待后续开发。</p>
    <div class="module-grid"><RouterLink v-for="module in businessModules" :key="module.path" :to="module.path" class="module-card">
      <div class="card-top"><span class="module-number">{{ module.number }}</span><span class="module-state">{{ module.path === '/access' ? '已接入' : module.path === '/operations' ? '部分接入' : '待接入' }}</span></div>
      <h3>{{ module.title }}</h3><p>{{ module.description }}</p><div class="card-bottom"><span>负责人 {{ module.owner }}</span><span aria-hidden="true">→</span></div>
    </RouterLink></div>
  </section>
</template>
