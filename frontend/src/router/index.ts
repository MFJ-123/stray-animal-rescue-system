import { createRouter, createWebHashHistory } from 'vue-router'
import { businessRoutes } from './modules/business'

export const router = createRouter({
  // Hash 路由使同一份前端资源既能在开发服务器运行，也能随 JAR 部署及直接刷新。
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('../views/HomeView.vue'), meta: { title: '项目工作台' } },
    ...businessRoutes,
    { path: '/login', component: () => import('../views/access/LoginView.vue'), meta: { title: '注册与登录' } },
    { path: '/access/roles', component: () => import('../views/access/ManageView.vue'), meta: { title: '管理角色配置', manageKind: 'roles' } },
    { path: '/access/review', component: () => import('../views/access/ManageView.vue'), meta: { title: '志愿者申请审核', manageKind: 'review' } },
    { path: '/access/volunteers', component: () => import('../views/access/ManageView.vue'), meta: { title: '志愿者资格管理', manageKind: 'volunteers' } },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
router.afterEach(to => { document.title = `${String(to.meta.title)} · 流浪动物救助系统` })
