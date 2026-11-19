# 项目路线图

状态：v1.0 已发布。下一项任务待 Project Owner 与 Tech Lead 确认；本文档不将未批准的方向写成版本承诺。

## 推进原则

项目通过小步开发练习软件工程，不一次生成完整软件。每个任务需明确目标、允许修改范围、排除项、测试和验收标准，再开始实现。

## v1.0 已交付

当前正式版本：**v1.0**（应用版本 `1.0.0`），目标平台 Windows x64。安装包附带 Java 21 运行时和 JavaFX。

- 发布页：[v1.0 Release](https://github.com/FH150174/AI-Developer-Hub/releases/tag/v1.0)
- 下载安装：[README · 下载使用](../README.md#下载使用)
- 功能清单：[README · 主要功能](../README.md#主要功能)

| 范围 | 已交付能力 |
| --- | --- |
| 本地项目 | 添加、改名、移除、路径检查、当前项目切换，以及 IDE、编辑器与终端入口 |
| Git 状态 | Git 安装检测、当前分支、最近提交、工作区变更明细 |
| 开发记录 | 按项目的开发日志、日期与基础 Markdown 预览；TODO 完成状态与优先级排序 |
| Agent 与环境 | Codex CLI、Claude CLI、Antigravity、PowerShell、WSL 启动入口与项目上下文；Java、Maven、Git、Node.js、Python、WSL、PATH 检测 |
| 设置与数据 | IDE / Agent 路径、默认项目目录、终端、主题、启动页；本地 XML 数据目录与旧数据迁移 |

### 文档入口

| 主题 | 文档 |
| --- | --- |
| 下载与安装 | [README · 下载使用](../README.md#下载使用) |
| 从源码运行 | [README · 从源码运行](../README.md#从源码运行) |
| 测试体系 | [测试体系](TESTING.md) |
| Windows 打包 | [Windows 打包](PACKAGING.md) |
| 本地数据 | [架构说明 · 本地数据目录](ARCHITECTURE.md#task-053-本地数据目录) |

## 历史里程碑

| 阶段 | 结果 |
| --- | --- |
| TASK-001 文档基线 | 建立需求、架构、开发流程与路线图四份初版文档 |
| 工程基础 | 建立 Java 21、JavaFX、Maven、JUnit 5 的可构建、可运行、可测试工程 |
| 核心功能 | 交付项目管理、Git 状态、开发日志、TODO、Agent 入口、环境检测与设置 |
| TASK-053 本地数据目录 | 使用 `%APPDATA%\AI-Developer-Hub\`，提供旧目录迁移与数据隔离 |
| TASK-057 测试体系 | 建立快速、集成与 Windows UI 分组及隔离规则 |
| TASK-058 Windows 打包 | 建立 jpackage、应用目录与 WiX 安装包流程 |
| v1.0 发布 | 发布带 Java 21 运行时的 Windows 安装包，安装与校验说明进入 README |
| v1.0 维护 | 修复项目列表写冲突、Windows 路径识别与隐藏页面重复 Git 查询 |

## 下一项待确认

下一项任务：**待确认**。v1.0 之后还没有任务通过范围与验收审批，也没有已承诺的新功能、版本号或发布日期。

候选来源：[GitHub Issues](https://github.com/FH150174/AI-Developer-Hub/issues) 与日常使用反馈。

确定方式：

1. 从实际使用问题或明确需求中提出候选。
2. 由 Project Owner 与 Tech Lead 定义任务目标、允许修改范围、排除项和验收标准。
3. 批准后再排期；未批准的方向不写入版本承诺。

新协作者可先阅读 [开发流程](DEVELOPMENT.md) 和 [Agent 协作规则](../AGENTS.md)，再参与任务定义。
