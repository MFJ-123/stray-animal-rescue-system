<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <section class="page-heading"><div><p class="eyebrow">业务模块 {{ route.meta.number }}</p><h1>{{ route.meta.title }}</h1><p class="lead">{{ route.meta.description }}</p></div></section>
  <section class="module-intro"><span class="module-state">待接入</span><h2>本模块的业务功能正在等待开发接入</h2><p>当前负责人：{{ route.meta.owner }}。现阶段可使用公共导航与请求工具，具体表单、业务接口及权限功能在后续任务中完成。</p><RouterLink to="/" class="text-link">返回项目工作台 →</RouterLink></section>
</template>
