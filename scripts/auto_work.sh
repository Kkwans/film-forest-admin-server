#!/bin/bash
# 贾维斯工作引擎 - 每10分钟自动执行
# 由系统 cron 驱动，每次生成真实 commit
WORKSPACE="/root/.openclaw/workspace"
TASK_FILE="$WORKSPACE/.current_task"
RESULT_FILE="$WORKSPACE/.task_result"
LOG="$WORKSPACE/logs/auto_work.log"

exec >> "$LOG" 2>&1
echo "[$(date '+%m-%d %H:%M:%S')] === 唤醒 ==="

cd "$WORKSPACE" || exit 1

# 读取当前任务
TASK=$(cat "$TASK_FILE" 2>/dev/null || echo "film-forest爬虫完善")

# 生成唯一任务标记
TS=$(date '+%m%d%H%M')

# 使用 openclaw agents run 触发贾维斯执行任务
# 任务输出到结果文件
timeout 480 openclaw agents run --agent main \
    --task "你是贾维斯。立即执行以下任务，完成后 commit push 到 GitHub：
    
当前项目：film-forest (影视资源网站)
任务：$TASK

执行步骤：
1. 读取 MEMORY.md 了解项目状态
2. 读取 projects/film-forest/backend/src/main/java/com/filmforest/crawler/core/CrawlerCore.java
3. 做一件真实、有意义的代码任务（参考 MEMORY.md 中的 Next Steps）
4. 单元测试验证
5. commit (message: 'auto: $TASK $TS') 并 push 到 GitHub
6. 把完成内容写入 $RESULT_FILE

重要：
- 必须产生真实可运行的代码，不能是注释或空函数
- commit 是唯一交付证明
- 完成后简单汇报到 $RESULT_FILE" 2>&1 | tail -50

echo "[$(date '+%m-%d %H:%M:%S')] 执行完成"
