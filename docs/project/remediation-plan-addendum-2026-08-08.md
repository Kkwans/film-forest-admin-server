# 影视森林正式整改计划补充项（2026-08-08）

> 状态：部分已确认，产品语义待补充 Grill Me
>
> 权威位置：`/volume2/Project/film-forest/docs/requirements/remediation-plan-addendum-2026-08-08.md`
>
> 源文档 SHA-256：`18bc4fecccbf2f6f34a7c25dffc406c4dcf4bcdaf0fc584e703100f18c5a232e`
>
> 适用总计划：`影视森林_Codex终极总控提示词_2026-08-07.md` 的 Phase 0–9
>
> 原则：不改变 Phase 顺序；新增能力进入最早具备正确前置条件的阶段。

## 1. 已确认事实

- 现有爬虫从 `pkmp4` 详情页的 `img`/`og:image` 直接取得海报 URL，写入五类内容表唯一的 `poster_url` 字段。
- 现有内容表没有海报来源、TMDB ID、TMDB poster path 或匹配诊断字段。
- 现有 `system_setting` 是全局键值配置，不支持每用户设置或敏感凭据隔离。
- 管理端侧栏组件已经使用 `sidebar-*` 语义类，但浅色主题的 `sidebar-*` token 仍定义为深色值，因此浅色模式没有真正适配。

## 2. 阶段映射

### Phase 1：爬虫任务生命周期可靠性

- 不实现 TMDB，也不修改管理端视觉样式。
- Job 模型和统计字段应允许后续 Phase 2 把 TMDB 匹配失败计入有限诊断，但不得提前耦合具体第三方 API。

### Phase 2：来源 Adapter、Fetcher 与 Parser

- 在来源海报提取之外增加独立的 TMDB 海报识别/获取适配器，不把 TMDB 调用塞进 `pkmp4` Parser。
- 依据内容类型分别使用 Movie Search/TV Search，再以 TMDB ID 查询详情或图片；保存稳定 ID、poster path、来源和匹配诊断，不只保存临时拼接 URL。
- 图片 URL 依据 TMDB `/configuration` 返回的 image base URL 与可用尺寸构造；中文海报优先，并定义英文/无语言海报回退顺序。
- 调用需要有限超时、限流、重试、401/429/5xx 分类和可取消边界；密钥不得出现在 URL 日志、异常、Job error summary 或测试夹具。
- 匹配必须使用标题、别名、年份、内容类型等结构化信号，并保留置信度/候选诊断；低置信度不得静默覆盖现有海报。

### Phase 5：管理端功能正确性

- 增加每用户海报偏好与 TMDB 凭据管理 API；服务端从认证身份取得 userId，禁止调用者替其他用户读写凭据。
- 凭据保存时加密；查询只返回 `configured`、掩码提示和最近验证状态，永不返回明文。
- 设置界面提供“来源站原始海报 / TMDB 智能识别”两种模式、凭据录入/替换/清除、连接验证和清晰的失败反馈。
- 展示 TMDB 要求的署名和免责声明，并提供 About/Credits 入口。

### Phase 6：UI 基础设计系统

- 优先修复管理端浅色 `sidebar-*` 语义 token，覆盖背景、前景、弱化文字、hover、active、border、focus ring 和移动端抽屉。
- 深色侧栏保留独立深色 token；不得通过组件内硬编码颜色制造第二套主题逻辑。

### Phase 8：管理端 UI/UX 精修

- 对侧栏执行 light/dark、desktop/mobile、键盘焦点和 reduced-motion 的真实浏览器截图与交互验收。
- 对海报模式与 TMDB 凭据设置执行空状态、未配置、验证中、验证失败、限流、保存成功和清除确认验收。

### Phase 9：最终验收

- 验证第三方凭据未进入 Git、镜像、日志和 API 响应。
- 验证 TMDB 署名、免责声明和图片回退在部署环境有效。

## 3. 待 Grill Me 决策

1. “每用户独立配置”的作用域：每部内容同时保留来源海报与 TMDB 海报，由每用户选择展示；还是仅管理员选择全站唯一海报来源。
2. TMDB 未匹配、密钥无效、限流或超时时，对已有海报的回退和保留策略。
3. TMDB 自动匹配的置信度阈值，以及低置信度候选是否需要管理员确认。
4. 已有内容的 TMDB 补全是按需触发、后台批量任务，还是两者都支持。

上述 P0/P1 产品语义确认前，不实现 TMDB 数据模型、API 或 UI。

## 4. TMDB 官方依据

- [Application Authentication](https://developer.themoviedb.org/docs/authentication-application)
- [Search and Query for Details](https://developer.themoviedb.org/docs/search-and-query-for-details)
- [Image Basics](https://developer.themoviedb.org/docs/image-basics)
- [Image Languages](https://developer.themoviedb.org/docs/image-languages)
- [FAQ / Attribution](https://developer.themoviedb.org/docs/faq)
