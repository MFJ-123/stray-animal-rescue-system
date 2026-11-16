import { createRouter, createWebHashHistory } from 'vue-router'
import { businessRoutes } from './modules/business'

export const router = createRouter({
  // Hash 路由使同一份前端资源既能在开发服务器运行，也能随 JAR 部署及直接刷新。
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('../views/HomeView.vue'), meta: { title: '项目工作台' } },
    ...businessRoutes,
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.afterEach(to => { document.title = `${String(to.meta.title)} · 流浪动物救助系统` })
