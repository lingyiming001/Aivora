# Aivora 前端

React 19 + TypeScript + Vite，使用 React Query 管理服务端数据，Zustand 保存两端认证状态。

```text
public/                       保留现有图片和图标 URL
src/
  assets/styles/              全局样式（按原顺序保留级联）
  components/                 Button、Avatar、MessageText、CharacterActions、AdminSummary、ConfirmDialog
  layouts/BasicLayout/        用户端与后台布局
  layouts/components/         头像菜单、主题切换、预算通知铃
  router/                     路由表、BrowserRouter、认证和权限守卫
  store/user/                 用户/管理员独立会话状态
  services/                   HTTP、Token 刷新、QueryClient
  hooks/                      全局认证 Hook
  utils/                      会话存储和公共操作错误处理
  constants/                  角色分类、用户等级、权限分组
  types/                      全局会话和管理员类型
  config/                     HTTP 参数、会话时效、组件主题配置
  pages/
    login/                    用户与管理员登录
    discovery/                角色发现与查询 API
    characters/               角色类型、共享封面、详情、编辑、我的角色
    conversations/            我的对话
    chat/                     聊天、会话创建 API、语音工具
    favorites/                收藏页面、API、类型
    profile/                  个人中心、API、类型、Hook、资料表单
    wallet/                   钱包和消耗统计
    messages/                 用户消息
    dashboard/                后台概览和审计
    system/
      user/                   用户管理
      review/                 角色审核与定价
      administrators/         管理员
      roles/                  角色及编辑表单
      permissions/            菜单权限
      access/                 权限查询 API、Hook、类型
      models/                 模型配置及路由
      status/                 Token 预算表单与类型
      data/                   财务与系统通用数据页
  App.tsx                     全局 Providers
  main.tsx                    样式、主题初始化、React 挂载
```

源码使用 `@/` 指向 `src/`。业务局部类型、API、Hook 和组件归属业务目录；仅在需要时创建目录，不复制第三方组件或创建空模块。

## 启动与构建

```sh
npm ci
npm run dev
npm run build
```

开发环境 `/api` 代理到 `http://localhost:8080`；生产继续使用同源 `/api`。未增加环境文件或新依赖。仓库根目录 `npm run format:check` 检查统一格式。

认证存储键、默认记住我一周、两端会话隔离、刷新并发合并、缓存失效规则和权限守卫保持原行为。Token 刷新使用原始 Axios，避免经过认证拦截器递归。API 请求统一使用 `services/request.ts`。

静态图片继续位于 `public/images`，原路由及 CSS 内容保留。此次仅拆分代码职责，未引入新的业务功能、状态库或额外组件封装。

## 操作确认

统一使用 `components/ConfirmDialog`：组件调用 `useConfirm()` 并等待 Promise，取消返回 false 后立即退出操作；Provider 在 BrowserRouter 内。删除按钮使用明确标签与危险色，保留实际影响说明；禁止浏览器原生 confirm 或静态 Modal.confirm。批量操作使用受控 ConfirmDialog，传入 loading/disabled，并保留失败反馈。

弹窗居中、支持三主题和手机布局；默认焦点位于取消，遮罩点击不关闭、Esc取消，处理中禁止重复触发。多个确认请求不会叠框，路由切换会取消待确认操作。

## 角色图片与详情

发现卡“查看详情”仅进入角色详情，不创建聊天；“开启对话”沿用原会话流程。卡片简介最多三行，详情显示完整介绍。

卡片、详情及创建页预览采用等比例完整显示。上传图片按长边最多512px缩小并转换JPEG，保留原比例与完整画面；原有5MB输入限制、编码大小限制及服务端校验不变。旧流程已永久裁剪的图片需重新上传原图，CSS无法恢复丢失内容。
