# AGENTS.md

# AI Developer Hub — Agent Collaboration Rules

本文件定义 AI Developer Hub 项目中所有 AI Agent 的工作规则。

所有 Agent 在开始任务之前，必须先阅读本文件，并遵守其中的约束。

---

## 1. Project Overview

项目名称：

AI Developer Hub

项目定位：

一个面向个人开发者的 Windows 桌面开发工具箱，用于集中管理本地开发项目、Git 状态、开发日志、TODO、AI Agent 入口和开发环境信息。

项目同时用于练习：

- Git / GitHub
- Java 桌面开发
- 软件工程流程
- 多 Agent 协作
- 测试
- Code Review
- 架构设计
- 版本管理

当前主要技术栈：

- Java 21
- JavaFX
- Maven
- JUnit 5
- IntelliJ IDEA
- Git
- Windows

---

## 2. Agent Roles

项目采用多 Agent 协作模式。

### ChatGPT

角色：

- Architect
- Tech Lead

主要职责：

- 需求分析
- 技术架构设计
- Task 拆分
- 任务范围定义
- 技术决策
- 最终验收
- 协调其他 Agent

ChatGPT 通常不作为主要代码实现者。

---

### Codex CLI

角色：

Primary Developer

主要职责：

- 实现明确指定的 Task
- 修改代码
- 编写测试
- 修复 Bug
- 执行相关测试
- 在允许范围内进行必要的小型重构

Codex 不负责项目级技术决策。

---

### Claude CLI

角色：

Reviewer

主要职责：

- Code Review
- 架构检查
- Bug 检查
- 可维护性检查
- 测试完整性检查
- 检查实现是否超出 Task 范围

默认情况下 Claude 只进行 Review，不直接修改代码。

---

### DeepSeek

角色：

Assistant Developer

适合处理：

- 简单代码
- 单元测试补充
- JavaDoc
- 文档整理
- DTO / Model
- 小型工具类
- 编译错误分析
- 重复性工作

核心架构决策不交给 DeepSeek 负责。

---

### Project Owner

项目所有者负责：

- 最终需求决定
- Git 操作
- 本地运行和验收
- 决定是否接受修改
- 决定是否 Merge

项目早期阶段：

以下 Git 操作必须由项目所有者本人执行：

- git add
- git commit
- git merge
- git push
- git tag

AI Agent 不得自行执行这些操作。

---

## 3. Required Reading

开始任何 Task 之前，Agent 必须阅读：

1. `AGENTS.md`
2. `docs/REQUIREMENTS.md`
3. `docs/ARCHITECTURE.md`

如果任务涉及开发流程，还需要阅读：

`docs/DEVELOPMENT.md`

如果任务涉及版本规划，还需要阅读：

`docs/ROADMAP.md`

---

## 4. Task Scope Rules

所有 Agent 必须严格遵守 Task Scope。

一个 Task 应尽量只解决一个明确问题。

例如：

正确：

`TASK-012: Implement ProjectRepository`

不推荐：

`Implement the whole project management module`

Agent 只能修改 Task 明确允许修改的文件或目录。

禁止：

- 顺手修改无关代码
- 顺手格式化整个项目
- 顺手重构无关模块
- 顺手升级依赖
- 顺手增加新功能
- 顺手修改 README
- 顺手修改架构

如果发现其他问题，应在任务报告中说明，而不是直接修改。

---

## 5. No Speculative Development

禁止 speculative development。

不要因为“以后可能需要”而提前实现功能。

例如：

当前任务只要求：

`读取 Git 当前分支`

则不要顺便实现：

- Git commit
- Git push
- Git log UI
- GitHub API
- Repository cloning

除非 Task 明确要求。

---

## 6. Architecture Rules

项目采用：

Feature-oriented + Layered Architecture

基本依赖方向：

UI

↓

Application / Service

↓

Domain

↓

Infrastructure

禁止反向依赖。

例如：

UI 可以调用 Service。

Service 可以调用 Repository。

Repository 可以访问文件系统、Git CLI 或其他基础设施。

Domain 不应该依赖 JavaFX UI。

---

## 7. Package Organization

基础包：

`com.fhaicl.aidevhub`

功能优先按照模块划分。

例如：

`project`

`git`

`notes`

`agent`

`environment`

`settings`

不要一开始将所有类统一放进：

- controller
- service
- repository
- model

项目优先采用 Feature-oriented 组织方式。

---

## 8. Dependency Rules

未经明确允许，不得新增依赖。

特别是修改：

`pom.xml`

之前必须先说明：

1. 为什么需要该依赖
2. 是否有不增加依赖的实现方式
3. 该依赖解决什么问题
4. 是否会影响项目架构

未经批准不得修改 `pom.xml`。

---

## 9. Git Rules

AI Agent 禁止自行执行：

`git add`

`git commit`

`git merge`

`git push`

`git rebase`

`git reset --hard`

`git tag`

除非项目所有者明确要求。

允许读取 Git 状态，例如：

`git status`

`git diff`

`git log`

`git branch`

这些命令只能用于了解当前状态。

不得改变 Git 历史。

---

## 10. Branch Strategy

项目采用：

Main + Feature Branch

主要分支：

`main`

功能分支示例：

`feature/project-manager`

`feature/git-status`

`feature/dev-notes`

修复分支：

`fix/git-path-error`

文档分支：

`docs/architecture`

`main` 分支必须尽量保持：

- 可编译
- 可运行
- 测试通过

---

## 11. Commit Convention

Commit 使用 Conventional Commits。

常用前缀：

`feat:`

新增功能。

`fix:`

修复 Bug。

`docs:`

文档修改。

`test:`

测试相关。

`refactor:`

不改变行为的代码重构。

`chore:`

构建、配置、维护工作。

示例：

`feat: add project model`

`fix: handle invalid project path`

`test: add GitService tests`

`docs: define initial architecture`

---

## 12. Testing Rules

完成代码任务后，应运行与本次任务相关的测试。

如果项目存在 Maven 测试：

优先运行：

`mvn test`

如果只需要测试某个模块，可以运行对应的最小测试范围。

Agent 不应为了“让测试通过”而删除失败测试。

如果测试失败，应报告：

- 哪个测试失败
- 失败原因
- 是否与本次修改有关

---

## 13. Error Handling

不要静默吞掉异常。

不要使用空的 catch 块。

错误处理应该做到：

- 能明确知道发生了什么
- 尽量提供可诊断信息
- 不暴露敏感信息
- UI 不应直接显示无意义的底层异常堆栈

---

## 14. Code Quality

代码应优先保证：

1. 可读性
2. 简单性
3. 可测试性
4. 可维护性

不要为了展示技巧而使用复杂实现。

优先：

Simple code

而不是：

Clever code

---

## 15. Comments

不要给显而易见的代码写无意义注释。

例如不要写：

```java
// Set project name
project.setName(name);
```

注释应该解释：

- 为什么这样做
- 特殊约束
- 设计原因
- 不明显的业务规则

---

## 16. Refactoring Rules

如果当前 Task 不要求重构：

不要进行大型重构。

允许进行：

- 极小范围重命名
- 消除明显重复
- 为完成当前任务所必需的小调整

如果需要较大重构，应停止并报告。

---

## 17. Documentation Rules

如果代码行为发生明显变化，但 Task 没有要求修改文档：

Agent 应报告文档可能需要更新。

不要自行修改无关文档。

涉及架构变化时：

必须更新：

`docs/ARCHITECTURE.md`

但架构变化必须先经过 Tech Lead 批准。

---

## 18. Forbidden Actions

未经明确授权，Agent 不得：

- 删除大量文件
- 修改 Git 历史
- 强制 Reset
- Push
- Merge
- 发布 Release
- 修改系统环境
- 安装系统软件
- 修改 IDE 设置
- 修改用户全局 Git 配置
- 修改与当前项目无关的目录
- 引入新的框架
- 替换当前技术栈

---

## 19. Task Completion Report

每个 Task 完成以后，Agent 必须输出简短报告。

报告至少包含：

### Changed

修改了哪些文件。

### Implemented

实现了什么。

### Tests

运行了什么测试。

### Result

测试是否通过。

### Out-of-scope

是否发现了任务范围外的问题。

### Git

确认没有执行：

- git add
- git commit
- git push

---

## 20. Conflict Handling

如果出现以下情况：

- Task 与 `ARCHITECTURE.md` 冲突
- Task 与 `REQUIREMENTS.md` 冲突
- 需要修改大量任务范围外代码
- 需要增加新依赖
- 需求存在明显歧义
- 可能破坏现有功能

Agent 应：

停止扩大修改范围。

然后报告问题。

由 Project Owner 或 Tech Lead 决定下一步。

---

## 21. Development Principle

项目开发遵循：

Small Task

↓

Small Change

↓

Test

↓

Review

↓

Commit

↓

Next Task

尽量保证：

一个 Task ≈ 一个可验证变化 ≈ 一个 Commit

---

## 22. Current Project Principle

AI Developer Hub 的目标不是让 AI 一次生成完整软件。

项目的重点是：

通过真实的小步开发流程学习软件工程。

因此：

宁可慢一点、清楚一点，也不要一次生成大量未经验证的代码。