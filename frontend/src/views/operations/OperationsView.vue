<script setup lang="ts">
import { hasRole } from '../../api/access'
</script>
<template><section class="page-heading"><div><p class="eyebrow">消息与运营</p><h1>运营工作台</h1><p class="lead">审计追溯与事件处理入口。</p></div></section><section class="form-panel"><h2>管理功能</h2><p v-if="['SYSTEM_ADMIN','RESCUE_ADMIN','ADOPTION_ADMIN'].some(hasRole)" class="field-help"><RouterLink to="/operations/audit" class="text-link">查看操作审计与纠错说明 →</RouterLink></p><p v-if="hasRole('SYSTEM_ADMIN')" class="field-help"><RouterLink to="/operations/events" class="text-link">查看事件投递监控 →</RouterLink></p><p v-if="!['SYSTEM_ADMIN','RESCUE_ADMIN','ADOPTION_ADMIN'].some(hasRole)" class="field-help">管理入口按账户职责开放，请使用相应管理账户登录。</p><p class="field-help">站内消息、举报和统计功能由后续业务模块接入。</p></section></template>
