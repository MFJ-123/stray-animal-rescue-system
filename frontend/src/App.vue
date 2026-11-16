<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { businessModules } from './router/modules/business'
const route = useRoute()
const menuOpen = ref(false)
function focusMain() { document.getElementById('main')?.focus() }
watch(() => route.fullPath, () => { menuOpen.value = false })
</script>

<template>
  <a class="skip-link" href="#main" @click.prevent="focusMain">跳至主要内容</a>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand" aria-label="流浪动物救助系统首页">
        <img src="/paw.svg" alt="" width="42" height="42" />
        <span>流浪动物救助<small>校园救助协作平台</small></span>
      </RouterLink>
      <button class="menu-toggle" :aria-expanded="menuOpen" aria-controls="primary-nav" @click="menuOpen = !menuOpen">{{ menuOpen ? '收起菜单' : '打开菜单' }}</button>
      <nav id="primary-nav" aria-label="主导航" :class="{ open: menuOpen }">
        <p class="nav-label">工作空间</p>
        <RouterLink to="/" class="nav-item" exact-active-class="selected"><span class="nav-symbol">⌂</span>项目工作台</RouterLink>
        <p class="nav-label modules-label">业务模块</p>
        <RouterLink v-for="module in businessModules" :key="module.path" :to="module.path" class="nav-item" active-class="selected">
          <span class="nav-number">{{ module.number }}</span>{{ module.title }}
        </RouterLink>
      </nav>
      <div class="sidebar-footer"><span class="small-dot"></span>基础工程 · 0.1.0</div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>{{ route.meta.title }}</span><span class="stage">开发协作版</span></header>
      <main id="main" tabindex="-1"><RouterView /></main>
      <footer class="page-footer">流浪动物救助系统 <span>Vue 3 · Spring Boot · MySQL</span></footer>
    </div>
  </div>
</template>
