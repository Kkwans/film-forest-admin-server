# 阶段零：记忆状态诊断详细指令

在执行任何恢复操作之前，先诊断当前记忆状态是否健康。如果健康则不需要恢复。

## 步骤 1：检查记忆文件完整性

```bash
# 指定要诊断的 agent（默认 main）
AGENT_ID="${1:-main}"

# 动态获取指定 agent 的 workspace 路径
WORKSPACE=$(openclaw agents list 2>/dev/null | sed -n "/^- $AGENT_ID/,/^- /{ /Workspace:/p }" | head -1 | awk '{print $2}')
WORKSPACE="${WORKSPACE/#\~/$HOME}"

# MEMORY.md
if [ -f "$WORKSPACE/MEMORY.md" ] && [ -s "$WORKSPACE/MEMORY.md" ]; then
  echo "✅ MEMORY.md 存在 ($(wc -l < "$WORKSPACE/MEMORY.md") 行)"
else
  echo "⚠️ MEMORY.md 缺失或为空"
fi

# daily notes
DAILY_COUNT=$(find "$WORKSPACE/memory" -maxdepth 1 -name "????-??-??.md" 2>/dev/null | wc -l | tr -d ' ')
echo "📅 Daily notes: ${DAILY_COUNT} 个"

# workspace 配置文件
for f in USER.md SOUL.md IDENTITY.md AGENTS.md TOOLS.md; do
  [ -f "$WORKSPACE/$f" ] && echo "✅ $f" || echo "⚠️ $f 不存在"
done
```

## 步骤 2：检查记忆索引状态

```bash
openclaw memory status --deep --json
```

重点关注输出中的：
- `sources`：是否包含 `["memory", "sessions"]`（默认已包含两者）
- `files` vs scan 中的 `totalFiles`：索引文件数是否匹配实际文件数
- `dirty`：是否有未索引的变更
- `vector.available`：向量搜索是否可用

## Sessions 索引说明

默认情况下 memorysearch 的 `sources` 已包含 `["memory", "sessions"]`，即 **session 日志默认就会被索引**。

但如果 sessions 索引异常，可手动确认配置：

```bash
openclaw config set agents.defaults.memorySearch.sources '["memory","sessions"]'
openclaw config set agents.defaults.memorySearch.experimental.sessionMemory true
```

配置后运行 `openclaw memory index --force` 重建索引。

### 索引源详解

| 源 | 索引内容 | 默认状态 |
|----|----------|----------|
| `memory` | `MEMORY.md`、`memory.md`、`memory/*.md`、`extraPaths` 中的 `.md` 文件 | ✅ 启用 |
| `sessions` | `~/.openclaw/agents/<agentId>/sessions/*.jsonl` 对话日志 | ✅ 默认启用 |

### sessions 索引的门控条件

即使在 `sources` 中配置了 `"sessions"`，还必须同时设置 `experimental.sessionMemory: true`，否则 sessions 会被过滤掉。这是 openclaw 源码中的双重门控设计。

## 判定与决策

| 状态 | 判定 | 操作 |
|------|------|------|
| MEMORY.md 非空 + daily notes ≥ 1 + 索引正常 | ✅ 健康 | **告知用户记忆正常，跳过恢复** |
| MEMORY.md 缺失/为空 + daily notes = 0 | ❌ 全量丢失 | 继续执行完整恢复流程 |
| MEMORY.md 存在但 daily notes 缺失，或索引 dirty | ⚠️ 部分缺失 | 仅恢复缺失部分（指定日期范围） |
| sources 缺少 memory 或 sessions | ⚠️ 索引不完整 | 确认配置正确后重建索引 |

### 判定原则

- **健康**：文件完整 + 索引同步
- **部分缺失**：只需定向修补，不必全量恢复。优先恢复缺失日期范围
- **全量丢失**：进入完整恢复流程，从 session 日志重建全部记忆
- **索引不完整**：先修配置、重建索引，再评估是否需要恢复内容
