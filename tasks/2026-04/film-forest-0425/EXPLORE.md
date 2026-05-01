# 七味网 (pkmp4.xyz) 站点结构分析报告

> 探索时间：2026-04-24
> 站点名称：七味网
> 域名：https://www.pkmp4.xyz/

---

## 一、站点概述

- **名称**：七味网（七味影院）
- **系统**：基于 **MacCMS**（某影视内容管理系统）搭建
- **内容**：电影、剧集、综艺、动漫等视频资源站
- **资源类型**：磁力链接（magnet）、网盘链接（百度网盘、夸克网盘、迅雷、UC网盘）、ed2k
- **注意**：不提供在线播放，仅提供下载资源列表

---

## 二、页面类型与 URL 结构

### 2.1 页面分类

| 路由 | 类型 | 说明 |
|------|------|------|
| `/` | 首页 | 展示最新/热门影视内容 |
| `/mv/{id}.html` | 电影详情页 | 电影基本信息 + 磁力/网盘下载列表 |
| `/vt/{page}.html` | 电视剧列表 | 分页展示剧集资源 |
| `/vs/{page}.html` | 综艺列表 | 分页展示综艺节目 |
| `/s/{page}.html` | 动漫列表 | 分页展示动漫内容 |
| `/apd.html` | 随机推荐 | APP 下载引导页 |
| `/gb.html` | 留言板 | 站点留言功能 |
| `/vp.html` | 未知 | 可能是其他类型内容 |

### 2.2 分页规则
- 列表页 URL 格式：`/{type}/{page}.html`
- 第一页：`/vt/1.html`
- 最大页数（电视剧）：至少 30+ 页
- 首页推荐列表约 20+ 个影视条目

### 2.3 ID 规律
- 电影详情页 ID：`/mv/{4-6位数字}.html`
- 示例：490613、400736、496168

---

## 三、页面结构详解

### 3.1 首页 (`/`)

**推荐列表（最新影视）**：
```
https://www.pkmp4.xyz/mv/400736.html  → 具体电影
https://www.pkmp4.xyz/mv/403764.html
... 约 20+ 条
```

**分类导航入口**：
- `/vt/1.html` — 电视剧
- `/vs/1.html` — 综艺
- `/s/1.html` — 动漫

### 3.2 电影详情页 (`/mv/{id}.html`)

**关键信息提取点**：

#### 电影基本信息（从 HTML 结构中提取）
| 字段 | CSS 选择器 / 提取方式 |
|------|------|
| 标题 | `<h1>` 或 `title` 属性 |
| 年份 | `<span class="year">` 或 title 年份括号 |
| 简介 | `<div class="movie-introduce">` 内部 `<p>` |
| 海报 | `<div class="li-img img"> img[src]` |
| 豆瓣链接 | `<a href="https://movie.douban.com/...">` |

**示例（电影 490613 - 挽救计划）**：
- 标题：挽救计划
- 年份：2026
- 豆瓣：https://movie.douban.com/subject/35010610
- 海报：https://i1.vvmp4.com/upload/vod/20260323-1/6f4863d8f9cea37641c716131d6511d2.jpg
- 简介：太阳正在被"吃"掉...（科幻剧情简介）

#### 下载资源列表（核心数据）

**结构**：
```html
<li class="down-list2">
    <p class="down-list3">
        <a href="{magnet或网盘URL}" title="{文件名含大小}">文件名[大小]</a>
    </p>
    <span>
        <a href="{同URL}" data-clipboard-text="{带dn参数的完整URL}" class="url-N">
            {下载类型文字}
        </a>
    </span>
</li>
```

**提取规则**：
```python
# magnet 链接
href^="magnet:" → class="url-1" → 磁力下载
href^="https://pan.baidu.com" → class="url-2" → 百度网盘
href^="https://pan.quark.cn" → class="url-3" → 夸克网盘
href^="https://pan.xunlei.com" → class="url-4" → 迅雷网盘
href^="https://drive.uc.cn" → class="url-5" → UC网盘
```

**示例数据（电影 490613）**：

| 类型 | URL | 文件名 | 格式 |
|------|-----|--------|------|
| 磁力 | magnet:?xt=urn:btih:deeb5700... | 1080p.HD修正中英双字.mp4[3.16G] | url-1 |
| 磁力 | magnet:?xt=urn:btih:0736c8e3... | 1080p.HD中英双字.mp4[3.16G] | url-1 |
| 百度 | https://pan.baidu.com/s/1jnZAqil6... | 挽救计划 4k | url-2 |
| 百度 | https://pan.baidu.com/s/1hEr9Py6... | 挽救计划 | url-2 |
| 夸克 | https://pan.quark.cn/s/7d385a020971 | 挽救计划 4k | url-3 |
| 夸克 | https://pan.quark.cn/s/b5997a69f9bf | 挽救计划 | url-3 |
| 迅雷 | https://pan.xunlei.com/s/VOq5DzIF... | 挽救计划 | url-4 |
| UC | https://drive.uc.cn/s/6dc058818b494 | 挽救计划 4k | url-5 |

#### 播放器信息
- 页面包含多个 `<ul class="player ckp gdt bf-w">` 列表
- 每个播放器名称在 `<div id="url">` 中定义（如：天堂、非凡、如意的）
- 播放器源数量：约 5-8 个

### 3.3 列表页 (`/vt/{page}.html`)

**结构解析**：
```python
# 每条数据包含：
<img src="{海报URL}" alt="{标题}" referrerpolicy="no-referrer">
  → class="li-img" 包裹
  → 标题在 <div class="li-bottom"> <h3> <a> 中
  → 额外标签（如"更新至HD"）在 <span class="bottom2"> 中
```

**提取字段**：
| 字段 | 提取方式 |
|------|----------|
| URL | `div.li-img a[href]` |
| 海报 | `div.li-img img[src]` |
| 标题 | `div.li-img img[alt]` |
| 状态标签 | `span.bottom2` 文字 |

**海报 CDN**：https://i1.vvmp4.com/upload/vod/{日期}/{hash}.webp 或 .jpg

---

## 四、AJAX / 接口

### 4.1 搜索建议接口

**URL**：`https://www.pkmp4.xyz/index.php/ajax/suggest`

**参数**：
| 参数 | 值 | 说明 |
|------|-----|------|
| mid | 1 | 模块ID（1=电影） |
| wd | {关键词} | 搜索词（UTF-8 URL编码） |
| limit | N | 返回数量 |

**返回格式**：
```json
{
  "code": 1,
  "msg": "数据列表",
  "page": 1,
  "total": 1,
  "list": [
    {
      "id": 490613,
      "name": "挽救计划",
      "en": "Project Hail Mary",
      "pic": "https://i1.vvmp4.com/upload/vod/20260323-1/6f4863d8f9cea37641c716131d6511d2.jpg"
    }
  ]
}
```

### 4.2 热度统计接口

**URL**：`https://www.pkmp4.xyz/index.php/ajax/hits`

**参数**：
| 参数 | 值 | 说明 |
|------|-----|------|
| mid | 1 | 模块ID |
| id | {电影ID} | 电影ID |
| type | update | 固定值 |

**返回格式**：
```json
{
  "code": 1,
  "msg": "ok",
  "data": {
    "hits": 2,
    "hits_day": 2,
    "hits_week": 2,
    "hits_month": 2
  }
}
```

### 4.3 其他潜在接口（未确认）

可能存在但需验证：
- `index.php/ajax/index` — 首页数据（需 Referer 头）
- `index.php/ajax/type` — 分类数据
- `index.php/ajax/score` — 评分数据
- `index.php/ajax/comment` — 评论数据

---

## 五、资源链接格式详解

### 5.1 磁力链接
```
magnet:?xt=urn:btih:{40位hash}
magnet:?xt=urn:btih:{hash}&dn={URL编码文件名}
```
- `dn` 参数：文件名（可选但推荐保留）
- 文件大小通常附在文件名后，如 `[3.16G]`

### 5.2 百度网盘
```
https://pan.baidu.com/s/{16位分享码}?pwd={4位提取码}
https://pan.baidu.com/s/{分享码}?pwd={提取码}&dn={URL编码文件名}
```

### 5.3 夸克网盘
```
https://pan.quark.cn/s/{分享码}
https://pan.quark.cn/s/{分享码}&dn={URL编码文件名}
```

### 5.4 迅雷网盘
```
https://pan.xunlei.com/s/{分享码}?pwd={提取码}
```

### 5.5 UC网盘
```
https://drive.uc.cn/s/{分享码}?public=1
```

### 5.6 ed2k（较少见）
```
ed2k://|文件|{大小}|{hash}|/
```

---

## 六、反爬虫分析

### 6.1 当前防护
- **无验证码**：页面可直接抓取
- **Referer 检查**：部分 AJAX 接口需要 `Referer: https://www.pkmp4.xyz/`
- **图片防盗链**：`referrerpolicy="no-referrer"` 避免防盗链
- **静态资源友好**：HTML 直接返回，无 JS 渲染需求（详情页）

### 6.2 潜在风险
- **频率限制**：大量请求可能触发 IP 限制
- **AJAX 接口变动**：基于 MacCMS，可能随版本更新变化
- **资源失效**：磁力/网盘链接有时效性

---

## 七、推荐爬取方案

### 7.1 列表页爬取（静态 HTML）
- **工具**：curl / web_fetch
- **URL 规律**：无需 API，直接解析 HTML
- **解析**：正则提取 `li-img` + `li-bottom` 配对

### 7.2 详情页爬取（静态 HTML）
- **工具**：curl / web_fetch
- **重点**：正则提取 `down-list2` 块中的磁力 + 网盘链接
- **防重**：同一电影可能有多个资源（不同分辨率/版本）

### 7.3 海报下载
- **CDN**：https://i1.vvmp4.com/
- **格式**：WebP 或 JPG
- **注意**：添加 `Referer: https://www.pkmp4.xyz/` 或直接下载

### 7.4 数据建模建议

```json
{
  "movie_id": 490613,
  "title": "挽救计划",
  "title_en": "Project Hail Mary",
  "year": 2026,
  "poster_url": "https://i1.vvmp4.com/upload/vod/20260323-1/6f4863d8f9cea37641c716131d6511d2.jpg",
  "douban_url": "https://movie.douban.com/subject/35010610",
  "introduce": "太阳正在被吃...",
  "resources": [
    {
      "type": "magnet",
      "url": "magnet:?xt=urn:btih:deeb5700...",
      "filename": "1080p.HD修正中英双字.mp4",
      "size": "3.16G",
      "resolution": "1080p"
    },
    {
      "type": "baidu_pan",
      "url": "https://pan.baidu.com/s/1jnZAqil6MjAixEGVZqzYqQ?pwd=ije4",
      "filename": "挽救计划 4k",
      "resolution": "4K"
    }
  ]
}
```

---

## 八、目录结构规范

```
tasks/2026-04/film-forest-0425/
+-- EXPLORE.md          # 本文档：站点结构分析
+-- SPEC.md             # 主人补充的具体需求（待填写）
+-- PLAN.md             # 爬虫执行计划
+-- DATA/               # 爬取的数据输出目录
|   +-- movies/         # 电影详情 JSON
|   +-- resources/      # 资源链接汇总
|   +-- posters/        # 海报图片
+-- LOG.md              # 爬取日志
```

---

## 九、待确认事项

1. **是否需要爬取电视剧** `/vt/`（数据量大很多）
2. **是否需要爬取动漫** `/s/` 和 **综艺** `/vs/`
3. **爬取深度**：全量爬取还是仅电影详情页？
4. **存储格式**：JSON / CSV / SQLite？
5. **是否需要下载海报图片**（还是只存 URL）？
6. **增量更新**：还是每次全量爬取？
