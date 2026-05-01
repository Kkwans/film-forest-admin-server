# 写作规范 -- WRITE_NORM.md

## 一、文件头格式

```
# 文件标题 -- YYYY-MM-DD
```

- 使用 `--`(两个ASCII横杠)作为分隔符，禁止使用：
  - EM DASH (U+2014)
  - EN DASH (U+2010)
  - 全角横杠 (U+FF0D)
- 禁止在文件标题中使用 Emoji（4字节UTF-8字符）

## 二、乱码根因

**根因**：NAS文件浏览器对无BOM文件默认用GBK/GB2312解析，导致中文乱码。

**解决方案**：所有workspace文件在开头添加UTF-8 BOM (`\xEF\xBB\xBF`)，强制文件浏览器识别为UTF-8编码。

**BOM格式**（所有文件必须包含）：
- 文件第一行必须是BOM标记：`\xEF\xBB\xBF`
- 然后才是正常的文件内容

## 三、写作规则

### 文件头（BOM + 标题行）
- 每文件开头加UTF-8 BOM
- 标题行纯ASCII（`--`分隔符）
- 标题行禁止Emoji

### 正文
- 中文/全角符号/Emoji/EM DASH均可使用
- 编码固定：UTF-8 with BOM

## 四、编码规范

- 文件编码：UTF-8 with BOM
- 换行符：LF
- 禁止使用：UTF-16 / GBK / 其他编码

## 五、历史问题文件（已修复）

以下文件曾有乱码问题（已重写为BOM格式）：
- SOUL.md / USER.md / AGENTS.md / MEMORY.md
- projects/film-forest/doc/PLAN.md
- projects/film-forest/doc/database-design.md
- tasks/film-forest-0425/PLAN.md
- tasks/interview-master-0422/PLAN.md

修复方式：重写为BOM格式。
