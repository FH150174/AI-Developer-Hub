# 开发流程

本文依据 [AGENTS.md](../AGENTS.md) 整理；如有冲突，以 AGENTS.md 和项目所有者的明确指令为准。

## 协作职责

| 角色 | 主要职责 |
| --- | --- |
| Project Owner | 最终需求决定、本地运行和验收、Git 操作、决定是否接受修改与 Merge |
| ChatGPT | Architect / Tech Lead，负责需求、架构、任务拆分、范围定义、技术决策与最终验收 |
| Codex CLI | Primary Developer，实现明确 Task、测试、修复 Bug、必要的小范围调整 |
| Claude CLI | Reviewer，默认只 Review，不直接修改代码 |
| DeepSeek | Assistant Developer，处理简单代码、补充测试、JavaDoc、文档和重复性工作 |

## 开始任务前

1. 阅读 AGENTS.md、[REQUIREMENTS.md](REQUIREMENTS.md) 和 [ARCHITECTURE.md](ARCHITECTURE.md)。
2. 涉及开发流程时阅读本文；涉及版本规划时阅读 [ROADMAP.md](ROADMAP.md)。
3. 明确 Task 目标、允许修改的文件或目录、排除项和验收标准。
4. 可通过 `git status`、`git diff`、`git log`、`git branch` 只读了解当前状态。

## 任务定义建议

每个 Task 应尽量对应一个可验证变化。任务说明应包含：

- 编号、名称和目标。
- 必要背景及需求来源。
- 允许修改的文件或目录。
- 明确不做的事项。
- 验收标准和相关验证方式。

## 实施与验证

按“小任务 → 小修改 → 测试 → Review → Commit → 下一任务”推进。Commit 由项目所有者执行。

- 不顺手修改无关代码、格式化整个项目、升级依赖、增加功能或修改 README。
- 不为未来可能出现的需求预先开发。
- 优先运行 `mvn test`；只涉及局部时，可选择对应的最小测试范围。
- 不删除失败测试以使测试通过。报告失败测试、原因及与本次修改的关系。
- 纯文档任务检查内容一致性、相对链接及范围，不要求运行不存在的构建。

如任务与需求或架构冲突、需要新增依赖、存在明显歧义或需要大量范围外修改，应停止扩大修改范围，报告并由 Project Owner 或 Tech Lead 决定下一步。

## 依赖与架构变更

未经明确允许，不新增依赖，不修改 `pom.xml`。提出依赖变更时，说明必要性、无新增依赖的替代方式、解决的问题及架构影响。

架构变更须先经 Tech Lead 批准，并同步更新架构文档。发现范围外问题时，在任务报告中说明，不自行修复。

## Git 与分支

采用 Main + Feature Branch。`main` 应尽量保持可编译、可运行、测试通过。

分支命名示例：`feature/project-manager`、`fix/git-path-error`、`docs/architecture`。

未经项目所有者明确要求，AI Agent 不执行 `git add`、`git commit`、`git merge`、`git push`、`git rebase`、`git reset --hard`、`git tag`，不得改变 Git 历史。

提交信息采用 Conventional Commits：`feat:`、`fix:`、`docs:`、`test:`、`refactor:`、`chore:`。这些约定不构成 AI Agent 执行提交的授权。

## 完成报告

每次任务结束后简短报告：

| 项目 | 内容 |
| --- | --- |
| Changed | 修改的文件 |
| Implemented | 完成的内容 |
| Tests | 执行的测试或检查 |
| Result | 验证结果及限制 |
| Out-of-scope | 发现的范围外问题 |
| Git | 确认是否执行了 `git add`、`git commit`、`git push` |
