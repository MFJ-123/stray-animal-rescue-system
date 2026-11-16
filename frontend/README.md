# 前端

Vue 3.5.45、Vue Router 4.6.4、Vite 8.3.8、TypeScript 5.9.3；Node 24.22.1、npm 11.6.2。npm ci 按 package-lock.json 安装，npm run dev 启动 127.0.0.1:5173。

所有调用通过 src/api/client.ts 访问同源 /api/v1，Vite 代理目标默认为 127.0.0.1:8080。需要修改时复制 .env.example 为 .env.local。客户端保留业务码、字段错误及请求号；401 触发 sar:session-expired 事件，供后续账户模块接入。不会自动重放写请求，页面保存并复用同一次操作的 requestKey。

公共布局位于 App.vue；路由位于 src/router/modules；各人添加自己的 views/<业务包> 和 api/<业务包>.ts。当前业务页只说明待开发范围，没有登录或业务表单。具体接入见[说明](../docs/基础工程接入说明.md)。

npm test 验证请求工具，npm run build 先类型检查再输出 dist。真实前后端启动后可 npm run test:e2e。
