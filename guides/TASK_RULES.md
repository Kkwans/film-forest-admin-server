# 任务规则 -- tasks 文件体系

> 加载时机: 新建任务 / 查询任务 / 执行任务 / 更新任务进度时

## tasks 与 projects 的关系

**tasks/** 是任务的规划、设计、过程记录和文档（"怎么做"和"做了什么"）。
**projects/** 是任务的工程产物——代码、配置、脚本（"做出来的东西"）。

核心规则：
- **所有 project 必须有关联的 task**（先有 task，再有 project）
- **不是所有 task 都有 project**（纯调研/文档类 task 无需 project）
- **project 开发过程中，对应的 task 必须持续更新**（LOG.md 记录进展）
- task 是项目的"大脑"，project 是项目的"身体"

## 任务文件体系

每个任务目录路径: `tasks/YYYY-MM/任务名-MMDD/`

| 文件 | 必需 | 用途 | 模板 |
|------|------|------|------|
| PLAN.md | 是 | 任务规划：目标、范围、阶段、里程碑、技术选型 | `guides/templates/PLAN.md` |
| DESIGN.md | 推荐 | 详细设计：架构、接口、数据模型、部署方案 | `guides/templates/DESIGN.md` |
| LOG.md | 推荐 | 执行日志：按时间线记录进展、问题、决策 | `guides/templates/LOG.md` |
| REVIEW.md | 完成时 | 任务复盘：成果总结、经验教训、待改进项 | `guides/templates/REVIEW.md` |
| EXPLORE.md | 按需 | 调研报告：技术调研、竞品分析、方案对比 | `guides/templates/EXPLORE.md` |
| SPEC.md | 按需 | 需求规格：功能需求、非功能需求、验收标准 | `guides/templates/SPEC.md` |

## 新建任务流程

1. 创建 `tasks/YYYY-MM/任务名-MMDD/PLAN.md`（参照模板）
2. 需要详细设计时创建 `DESIGN.md`
3. 开始开发时创建 `LOG.md` 记录进展
4. 如有关联 project，在 `projects/项目名/` 创建工程目录
5. 在 MEMORY.md 中建立索引
6. 任务完成时创建 `REVIEW.md` 做复盘
