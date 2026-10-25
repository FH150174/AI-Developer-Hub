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
****