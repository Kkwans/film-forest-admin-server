---
name: memory-recovery
description: 管理 OpenClaw 的记忆系统。包括：1) 日常对话中主动识别并写入值得记住的信息；2) 从 session 历史日志（JSONL）中恢复/重建丢失的记忆。当对话中出现值得记忆的内容、记忆文件丢失/损坏、需要补全、或用户要求从历史对话恢复记忆时使用。
metadata: { "openclaw": { "emoji": "🧠", "requires": { "bins": ["jq"] } } }
---

# memory-recovery

管理 OpenClaw 记忆系统：**主动记忆** + **记忆恢复**。

- **主动记忆**：在日常对话中识别值得记住的信息，及时写入 memory 文件和 workspace 配置
- **记忆恢复**：从 session 日志（JSONL）中提取、深度加工、提炼，恢复 `MEMORY.md` + `memory/YYYY-MM-DD.md` 及 workspace 配置文件

---

## 📝 主动记忆（日常对话中使用）

### 什么时候应该写记忆

在对话进行中，遇到以下任何情况时，**立即**写入记忆，不要等对话结束：

| 触发条件 | 写入目标 | 示例 |
|----------|----------|------|
| 用户做出了技术决策 | `memory/YYYY-MM-DD.md` | "我们用 Redis 而不是 Memcached" |
| 解决了一个问题（尤其是踩坑） | `memory/YYYY-MM-DD.md` | "原来是 nginx 超时导致的" |
| 用户表达了偏好或习惯 | `USER.md` | "叫我老王"、"我习惯用 vim" |
| 用户调整了 Agent 的行为 | `SOUL.md` / `AGENTS.md` | "以后回复简洁一点"、"部署前先跑测试" |
| 用户设定了 Agent 身份 | `IDENTITY.md` | "你叫小助手" |
| 发现了重要的技术细节 | `MEMORY.md` | "这个 API 有 rate limit，每分钟 100 次" |
| 用户说了 "记住"、"以后"、"别忘了" | 按内容路由到对应文件 | "记住，这个仓库不要用 npm" |
| 完成了一个较大的任务 | `memory/YYYY-MM-DD.md` | 任务摘要 + 关键决策 + 经验教训 |
| 对话即将结束（用户说再见/感谢） | `memory/YYYY-MM-DD.md` | 本次对话的关键收获 |

### 写记忆的信号词

当对话中出现这些词时，**高度警觉**，几乎一定要写入记忆：

- 决策类："决定"、"改为"、"选择"、"放弃"、"确认"、"就用"
- 发现类："发现"、"原来"、"终于"、"找到了"、"根本原因"、"搞清楚了"
- 教训类："踩坑"、"注意"、"以后"、"记住"、"千万别"、"又"、"坑"、"别忘了"
- 指令类："叫我"、"你叫"、"用中文"、"语气"、"不要"
- 重要类："关键"、"必须"、"重要"、"核心"、"红线"

### 怎么写

1. **立即写**：发现值得记的信息后，在当前回复中就完成写入，不要攒着
2. **记因果**：不只记 "做了什么"，更记 "为什么这么做"  
3. **标闪光灯**：重要发现/决策/教训用 `⚡` 标记
4. **加标签**：`#nginx` `#部署` `#踩坑` 便于检索
5. **路由正确**：用户画像 → `USER.md` / 行为规则 → `AGENTS.md` / 事件经验 → daily notes

### 主动记忆的写入格式

写入 `memory/YYYY-MM-DD.md` 时，追加到文件末尾：

```markdown
### HH:MM - 主题摘要 #标签
- 背景/起因
- 关键过程或决策（附原因）
- ⚡ 重要发现或教训
- 结果/结论
```

如果文件不存在，先创建并写入标题 `# YYYY-MM-DD Daily Notes`。

### 不要记什么

- 纯寒暄和闲聊（除非透露了用户偏好）
- 重复确认（"好的"、"明白了"）
- 临时性的调试过程（除非踩了坑）
- 已经记过的信息（先检查再写）

---

## 记忆架构

| 层级 | 文件 | 作用 |
|------|------|------|
| **情景记忆** | `memory/YYYY-MM-DD.md` | 按事件/主题组织的每日记忆，保留上下文和因果关系 |
| **语义记忆** | `MEMORY.md` | 从情景记忆中提炼的抽象知识，脱离具体事件独立存在 |
| **闪光灯记忆** | daily notes 中 `⚡` 标记 | 重大决策/踩坑/发现，永不折叠或省略 |

## 可恢复的 Workspace 文件

| 文件 | 恢复时提取什么 |
|------|----------------|
| `USER.md` | 称呼、时区、语言偏好、沟通风格 |
| `SOUL.md` | 用户对 Agent 语气/风格的调整指令 |
| `IDENTITY.md` | Agent 名字、物种、气质、emoji、头像 |
| `AGENTS.md` | 用户设定的行为约束和注意事项 |
| `TOOLS.md` | 工具使用的自定义指令 |

**信息路由**：用户自己的信息 → `USER.md` / Agent 怎么说话 → `SOUL.md` / Agent 是谁 → `IDENTITY.md` / Agent 怎么做事 → `AGENTS.md` / 工具使用 → `TOOLS.md` / 具体事件和经验 → `MEMORY.md`

## Session 日志

```
~/.openclaw/agents/<agentId>/sessions/
├── sessions.json              # session key → session ID 映射
├── <session-id>.jsonl         # 每个 session 的完整对话记录
└── <session-id>.deleted.<ts>  # 已删除的 session
```

`<agentId>` 从 system prompt Runtime 行 `agent=<id>` 读取，通常为 `main`。

JSONL 每行一个 JSON 对象，type 为 `session`（元信息）、`model_change`（模型切换）或 `message`（对话消息，role: user/assistant/toolResult，content 数组含 text/toolCall/thinking 块）。

---

## 🔄 完整恢复流程

### 阶段零：记忆状态诊断（必须首先执行）

> 详细诊断步骤（检查脚本、索引状态解读、sessions 索引配置）见 [references/memory-diagnosis.md](references/memory-diagnosis.md)

**在执行任何恢复操作之前，先诊断当前记忆状态是否健康。如果健康则不需要恢复。**

1. 检查记忆文件完整性（`MEMORY.md`、`memory/*.md`、workspace 配置文件）
2. 检查记忆索引状态（`openclaw memory status --deep --json`）

| 判定 | 操作 |
|------|------|
| ✅ 健康（文件完整 + 索引正常） | **告知用户记忆正常，跳过恢复** |
| ❌ 全量丢失（MEMORY.md 空 + daily notes = 0） | 继续执行完整恢复流程 |
| ⚠️ 部分缺失 | 仅恢复缺失部分（指定日期范围） |
| ⚠️ 索引不完整（sessions 未索引） | 建议用户启用 session 索引后重建 |

**如果诊断结果为「健康」，直接告知用户并结束流程，不进入后续阶段。**

---

### 前置步骤：确认恢复范围（必须）

**诊断确认需要恢复后，必须先向用户确认恢复范围。** 不要直接开始恢复。

1. 运行 session 概览：

```bash
SESSIONS_DIR=~/.openclaw/agents/main/sessions
for f in "$SESSIONS_DIR"/*.jsonl; do
  [ -f "$f" ] || continue
  ts=$(head -1 "$f" | jq -r '.timestamp // empty' 2>/dev/null)
  date=$(echo "$ts" | cut -dT -f1)
  msgs=$(jq -c 'select(.type=="message")' "$f" 2>/dev/null | wc -l)
  echo "$date | ${msgs} msgs | $(basename "$f" .jsonl)"
done | sort
```

2. 向用户展示可用日期，询问恢复范围（全部 or 指定日期区间）
3. **等待用户回复后才能继续**

### 阶段一：智能提取原始素材

运行提取脚本，生成 `memory/YYYY-MM-DD-recovered.md` 临时文件：

```bash
bash scripts/extract-sessions.sh [agent_id] [output_dir] [date_from] [date_to]
```

**提取策略**：
- user 消息完整保留，assistant 消息仅取 `type=="text"` 块
- 跳过 `thinking`、`toolCall`、`toolResult`
- 不做字符截断

产出：每个日期一个 `*-recovered.md`，包含该日全部 session 的完整对话文本。

### 阶段二：深度加工为正式记忆文件

> 详细加工策略（信号词表、正反例、格式模板）见 [references/deep-processing.md](references/deep-processing.md)

**核心操作：**

1. 逐个读取 `memory/*-recovered.md`
2. 深度理解对话内容，运用以下策略：
   - **因果关系提取**：记录"为什么"而非只记"做了什么"
   - **闪光灯识别**：通过信号词识别高价值记忆，标 `⚡`
   - **主题聚合**：同一事件的多轮对话归到同一主题下
   - **标签 + 交叉引用**：每个主题加 `#tag`，跨日期加 `→ 参见`
   - **信息分类**：区分记忆类（→ daily notes）和配置类（→ workspace 文件）
3. 生成正式 `memory/YYYY-MM-DD.md`（已存在则合并）
4. 删除对应的 `*-recovered.md`

### 阶段三：提炼并更新 Workspace 文件

#### 步骤 A：更新 Workspace 配置文件

> 各文件的信号词和更新策略详见 [references/workspace-update.md](references/workspace-update.md)

从 daily notes 中收集配置类信息，读取已有 workspace 文件，按路由规则更新。

**关键原则**：文件不存在时不创建 / 保留已有内容 / 冲突时新优先 / 不确定时跳过。

#### 步骤 B：提炼长期记忆到 MEMORY.md

1. 读取所有 `memory/YYYY-MM-DD.md` 和当前 `MEMORY.md`
2. 提炼语义记忆（技术备忘、经验教训、待办等）
   - 已路由到 workspace 配置文件的信息**不重复写入**
3. 追加更新 MEMORY.md，每条标注来源 `(→ YYYY-MM-DD)`
4. 更新时间戳，移除已完成的待办

**Schema 动态演化**：MEMORY.md 不是固定模板。最小骨架为「技术备忘 / 经验教训 / 待办」三个分类，其余按需创建。分类超 15 条考虑拆分，长期为空则移除。

### 阶段四：刷新记忆索引

#### 步骤 A：确认索引源配置

```bash
openclaw memory status --json | jq '.[0].status.sources'
```

如果输出不包含 `"sessions"` 且用户希望 session 也被索引，先配置：

```bash
openclaw config set agents.defaults.memorySearch.sources '["memory","sessions"]'
openclaw config set agents.defaults.memorySearch.experimental.sessionMemory true
```

#### 步骤 B：重建索引

```bash
openclaw memory index --force
```

`--force` 确保完全重建索引，包含新恢复的 memory 文件和（如果已配置）session 文件。

成功则输出 `✅ 记忆索引已刷新`，失败则输出 `⚠️ 刷新失败，跳过`，不阻塞流程。

---

## 高级用法

### 仅恢复指定日期

```bash
bash scripts/extract-sessions.sh main 2026-03-05 2026-03-10
```

### 仅恢复指定 session

```bash
jq -r 'to_entries[] | "\(.key) → \(.value)"' ~/.openclaw/agents/main/sessions/sessions.json
```

### 跨 session 搜索关键词

```bash
SESSIONS_DIR=~/.openclaw/agents/main/sessions
for f in "$SESSIONS_DIR"/*.jsonl; do
  if jq -r '.message.content[]?.text // empty' "$f" 2>/dev/null | rg -qi "关键词"; then
    date=$(head -1 "$f" | jq -r '.timestamp' | cut -dT -f1)
    echo "Found in: $(basename "$f") ($date)"
  fi
done
```

## 注意事项

1. **遵守 AGENTS.md 安全规范**：执行任何阶段前先读取当前 `AGENTS.md`，确保恢复过程不违反其中的安全规则和文件操作限制
2. **隐私安全**：提取的信息仅写入 workspace 内的文件，不外发
3. **增量合并**：所有文件已存在时合并，不清空重写
4. **去重**：已路由到 workspace 配置文件的信息不重复写入 MEMORY.md
5. **时区转换**：JSONL timestamp 为 UTC，写入时转换为用户时区
6. **大文件分块**：超大 session（>5MB）分块处理
7. **System 消息过滤**：`System:` 开头的元信息提取时过滤
8. **Compaction 标记**：`[compacted]` 摘要消息也是有价值的信息源
9. **recovered 文件是临时的**：整理完成后必须删除
10. **闪光灯记忆保护**：`⚡` 标记的条目永不省略或折叠
11. **标签一致性**：同一概念统一标签命名（如 `#nginx` 而非 `#Nginx`）
12. **交叉引用双向性**：添加 `→ 参见` 时在目标文件也添加反向链接
13. **Workspace 文件不主动创建**：只更新已存在的配置文件，不存在时跳过
14. **配置类信息只写确定的**：只有用户明确表达的指令才写入，不推测意图
