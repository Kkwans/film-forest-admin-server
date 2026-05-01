# 工作区规范

## 目录结构
```
/root/.openclaw/workspace/
├── AGENTS.md / SOUL.md / IDENTITY.md / USER.md   # 全局核心文件（不移动）
├── HEARTBEAT.md / TOOLS.md                       # 工具配置文件
├── memory/                                        # 记忆系统
│   ├── MEMORY.md                                 # 长期记忆索引
│   └── YYYY-MM-DD.md                             # 当日对话记忆
├── rules/                                         # 执行准则
│   ├── MEMORY_RULES.md                           # 记忆规范
│   └── WORKSPACE_RULES.md                        # 工作区规范
├── projects/                                     # 正式项目（核心）
│   └── 项目名/                                   # 项目文件夹
│       ├── frontend/                             # 前端代码
│       ├── backend/                               # 后端代码
│       ├── database/                             # 数据库脚本
│       ├── deploy/                               # 部署脚本/配置
│       ├── doc/                                  # 文档/设计
│       └── release/                              # 产物（可选）
├── tasks/                                         # 任务目录
│   └── YYYY-MM/                                  # 按月分类
│       └── 任务名-月日/                           # 具体任务
│           ├── PLAN.md                           # 任务规划
│           └── ...                               # 其他文件
└── skills/                                        # 技能（ClawHub）
```

## 文件存放原则
1. **根目录**：只放全局核心文件，不随意新增
2. **正式项目** → projects/项目名/（frontend/backend/database/deploy/doc/release）
3. **任务文件** → tasks/YYYY-MM/任务名-月日/，按月份分类
4. **规则文件** → rules/，不能大段文字，精简引用
5. **临时文件** → /tmp/，不放在workspace

## 新建任务流程
1. 创建 tasks/YYYY-MM/任务名-月日/
2. 在 MEMORY.md 中建立索引
3. 规划放 PLAN.md
