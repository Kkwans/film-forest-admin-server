# 影视森林 - 自动开发任务

## 本轮完成（2026-05-01 13:36）

### 本轮新增完成
- ✅ **补全 drama/anime/variety/short 列表页 region 筛选**：
  - 前端：`/app/drama/page.tsx`、`/app/anime/page.tsx`、`/app/variety/page.tsx`、`/app/short/page.tsx` 全部改为后端 API 筛选（原来只有 movie 页正确使用后端筛选，其余4个分类页都是前端过滤）
  - 后端：Service 层统一新增 `region` LIKE 查询参数（DramaService/VarietyService/AnimeService/ShortDramaService + Impl）
  - Controller 层：DramaController/AnimeController/VarietyController/ShortDramaController 统一接收 `region` 参数
  - API 层：`api.ts` 中 dramaApi/animeApi/varietyApi/shortDramaApi 补充 `region` 参数签名

### 技术调查
- 后端 `/api/movies` 已支持 `year`/`region`/`genre` 参数筛选（MovieServiceImpl 完整实现）
- 前端电影页 `movie/page.tsx` 筛选器正确传递 region/year 参数给 API
- 后端 LIKE 查询 region（需注意 JSON 数组场景，如"香港,台湾"会被"香港"匹配）
- 数据库存在中文乱码问题（JDBC 连接未指定字符集，LOG 文件中 æµ«æµªåœ°ç�ƒ 等乱码）
- 爬虫框架完整（七味网 QiweiCrawler + CrawlerEngine + 调度逻辑）
- 七味网目标站点未知（qw.com 非真实地址，需确认实际影视站）
- TMDB 爬虫框架完整（`TmdbCrawler.java`），待填 API Key

### 待完成任务（按优先级）
- ✅ P0 - Maven 编译部署（crawler 模块已整合编译）

### P1 - 用户端完善
- ✅ 剧集详情页 /drama/[id]
- ✅ 综艺详情页 /variety/[id]
- ✅ 动漫详情页 /anime/[id]
- ✅ 短剧详情页 /short/[id]
- ✅ 搜索结果页 /search（功能正常，URL 参数兼容）
- ✅ 剧集/综艺/动漫/短剧列表页 region/year 筛选（已接入后端 API）

### P1 - 管理端完善
- ✅ 爬虫管理 /crawler
- ✅ 内容管理 /content
- ✅ 资源管理 /resources
- ✅ 系统设置 /settings
- ✅ 统计页面 /stats

### P2 - 爬虫开发
- ✅ 后端爬虫 API（/api/crawler/*）- 已有完整源码
- ✅ **TMDB 爬虫实现**（`TmdbCrawler.java`，待填 API Key）
- ❌ 七味网爬虫核心对接（需确认目标站点真实 URL，当前占位）
- ❌ Docker 容器化（docker.sock 权限限制，Kkwans 用户无法直接运行 docker）

### P3 - 数据层
- ✅ 前端筛选器已接入后端 API（year/region 参数后端已支持）
- ✅ 搜索分页优化（已接入 total/size/page 分页）

### 建议下一步
1. **填入 TMDB API Key**（免费）：注册 https://www.themoviedb.org/settings/api，替换 `TmdbCrawler.API_KEY`
2. Docker 容器化需 Kkwans 执行 `sudo usermod -aG docker Kkwans` 并重新登录
3. 数据库字符集修复（JDBC URL 已加 `characterEncoding=utf8`，乱码可能来自日志框架编码）
4. 详情页播放功能（在线播放/磁力下载目前是 mock 数据）
5. 用户收藏/历史记录功能