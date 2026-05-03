# 影视森林用户端 (film-forest-client)

面向普通用户的影视资源浏览/搜索/下载网站。

## 技术栈

| 技术 | 说明 |
|------|------|
| Next.js 14 | React 全栈框架（App Router） |
| TypeScript | 类型安全 |
| TailwindCSS v4 | 原子化 CSS |
| Shadcn UI | 高质量组件库 |
| Zustand | 状态管理 |
| Axios | HTTP 客户端 |

## 功能

- [x] 响应式布局（移动端 + PC 自适应）
- [x] 深色主题 UI
- [x] 分类导航（电影/剧集/综艺/动漫/短剧）
- [x] 搜索功能
- [x] 热门推荐展示
- [ ] 电影/剧集列表页
- [ ] 详情页 + 资源链接
- [ ] 筛选/排序

## 开发

```bash
# 安装依赖
npm install

# 本地开发
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run start
```

## 目录结构

```
src/
├── app/                    # Next.js App Router 页面
│   ├── page.tsx           # 首页
│   ├── layout.tsx        # 根布局
│   └── globals.css       # 全局样式
├── components/            # 业务组件
│   ├── Header.tsx
│   └── Footer.tsx
├── components/ui/        # Shadcn UI 组件
├── lib/                  # 工具函数
│   ├── api.ts           # API 请求封装
│   └── utils.ts         # 工具函数
└── stores/               # Zustand 状态管理
```

## 关联项目

- 后端 API: [film-forest-server](https://github.com/Kkwans/film-forest-server)
- 管理端: [film-forest-admin](https://github.com/Kkwans/film-forest-admin)

## License

MIT