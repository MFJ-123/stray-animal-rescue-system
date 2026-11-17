import type { RouteRecordRaw } from 'vue-router'

// 各模块在此目录添加自己的路由文件。当前页面只介绍分工，不提供未实现的业务操作。
export const businessModules = [
  { path: '/access', title: '账户与访问', owner: 'A', number: '01', description: '注册登录、角色权限与志愿者资格。' },
  { path: '/clue', title: '救助线索', owner: 'B', number: '02', description: '线索上报、核实、合并与进展查询。' },
  { path: '/rescue', title: '救助任务', owner: 'B', number: '03', description: '任务认领、协作、转派与执行验收。' },
  { path: '/animalcare', title: '动物与照护', owner: 'C', number: '04', description: '动物档案、照护记录、交接及发布。' },
  { path: '/adoption', title: '领养申请', owner: 'D', number: '05', description: '申请审核、预留、条件确认与领养交接。' },
  { path: '/followup', title: '退养与回访', owner: 'E', number: '06', description: '回访核验、退养接回及信用复核。' },
  { path: '/operations', title: '消息与运营', owner: 'A / E', number: '07', description: '消息、举报、统计及操作追溯。' },
] as const

export const businessRoutes: RouteRecordRaw[] = businessModules.map(module => ({
  path: module.path,
  component: module.path === '/access' ? () => import('../../views/access/AccountView.vue') : () => import('../../views/ModuleView.vue'),
  meta: { ...module },
}))
