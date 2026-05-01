#!/bin/bash
# 从 OpenClaw session JSONL 日志中智能提取对话内容
# 用法: bash extract-sessions.sh [agent_id] [date_from] [date_to]
# workspace 路径通过 openclaw agents list 自动获取

AGENT_ID="${1:-main}"
DATE_FROM="${2:-}"  # 留空表示全部，格式 YYYY-MM-DD
DATE_TO="${3:-}"    # 留空表示全部，格式 YYYY-MM-DD

# 动态获取指定 agent 的 workspace 路径
OUTPUT_DIR=$(openclaw agents list 2>/dev/null | sed -n "/^- $AGENT_ID/,/^- /{ /Workspace:/p }" | head -1 | awk '{print $2}')
OUTPUT_DIR="${OUTPUT_DIR/#\~/$HOME}"
if [ -z "$OUTPUT_DIR" ]; then
  echo "❌ 无法获取 workspace 路径，请确认 openclaw 已安装并配置"
  exit 1
fi
SESSIONS_DIR="$HOME/.openclaw/agents/$AGENT_ID/sessions"
MEMORY_DIR="$OUTPUT_DIR/memory"

mkdir -p "$MEMORY_DIR"

echo "=== 智能提取对话内容 ==="
echo "Sessions: $SESSIONS_DIR"
echo "Output:   $MEMORY_DIR"
[ -n "$DATE_FROM" ] && echo "From:     $DATE_FROM"
[ -n "$DATE_TO" ] && echo "To:       $DATE_TO"
echo ""

for f in "$SESSIONS_DIR"/*.jsonl; do
  [ -f "$f" ] || continue
  date=$(head -1 "$f" | jq -r '.timestamp' 2>/dev/null | cut -dT -f1)
  [ -z "$date" ] && continue

  # 日期范围过滤
  if [ -n "$DATE_FROM" ] && [[ "$date" < "$DATE_FROM" ]]; then continue; fi
  if [ -n "$DATE_TO" ] && [[ "$date" > "$DATE_TO" ]]; then continue; fi

  DAILY_FILE="$MEMORY_DIR/${date}-recovered.md"

  # 智能提取：完整保留 user 消息，assistant 仅取 text 块，跳过 toolResult/thinking/toolCall
  content=$(jq -r '
    select(.type=="message") |
    select(.message.role=="user" or .message.role=="assistant") |
    (.timestamp | split("T") | .[1] | split(".")[0] | .[0:5]) as $time |
    (.message.role) as $role |
    [.message.content[]? | select(.type=="text") | .text] | join("\n") |
    select(length > 0) |
    "[\($time)] \($role):\n\(.)"
  ' "$f" 2>/dev/null)

  if [ -n "$content" ]; then
    echo "  📅 $date - $(basename "$f" .jsonl)"
    if [ ! -f "$DAILY_FILE" ]; then
      echo "# $date Raw Recovery (临时文件，整理后删除)" > "$DAILY_FILE"
      echo "" >> "$DAILY_FILE"
    fi
    echo "## Session: $(basename "$f" .jsonl)" >> "$DAILY_FILE"
    echo "" >> "$DAILY_FILE"
    echo "$content" >> "$DAILY_FILE"
    echo "" >> "$DAILY_FILE"
    echo "---" >> "$DAILY_FILE"
    echo "" >> "$DAILY_FILE"
  fi
done

echo ""
echo "=== 提取完成 ==="
ls -la "$MEMORY_DIR"/*-recovered.md 2>/dev/null
