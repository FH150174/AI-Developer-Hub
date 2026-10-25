# 项目架构

状态：第一版架构基线，整理自 [AGENTS.md](../AGENTS.md)，不引入额外架构决策。

## 技术基线

| 项目 | 选择 |
| --- | --- |
| 语言 | Java 21 |
| 桌面 UI | JavaFX |
| 构建 | Maven |
| 测试 | JUnit 5 |
| 目标平台 | Windows |
| 基础包 | `com.fhaicl.aidevhub` |

当前文档不表示项目已具备构建配置或可运行代码。新增依赖、修改 `pom.xml` 或调整架构，应遵守 AGENTS.md 中的授权规则。

## 组织方式

采用 Feature-oriented + Layered Architecture：先按功能划分模块，再在功能内部按需要划分职责。不要预先创建所有模块或空层级。

已明确的功能包方向为 `project`、`git`、`notes`、`agent`、`environment`、`settings`。TODO 的归属尚待具体任务确认。不将全项目的类统一堆放到顶层 `controller`、`service`、`repository` 或 `model` 包中。

## 分层与依赖方向

遵循 AGENTS.md 定义的基本方向：

```text
UI
 ↓
Application / Service
 ↓
Domain
 ↓
Infrastructure
```

| 层 | 职责与约束 |
| --- | --- |
| UI | 承载 JavaFX 界面、用户输入与结果展示；调用 Service |
| Application / Service | 协调用例和业务流程；可调用 Repository |
| Domain | 表达业务模型与业务规则；不依赖 JavaFX UI |
| Infrastructure | 处理文件系统、Git CLI 等外部资源访问 |

禁止反向依赖。Repository 可以访问文件系统、Git CLI 等基础设施；其接口、实现的具体归属及调用关系在对应 Task 中明确，不在文档初始化阶段增加新的架构约定。

## 错误处理与测试边界

- 不使用空的 catch 块，不静默吞掉异常。
- 外部访问失败时保留有助于诊断的信息，同时避免泄露敏感信息。
- UI 提供可理解的错误提示，不直接显示无意义的底层异常堆栈。
- 业务规则和外部资源访问保持职责清晰，便于针对任务编写测试。
- 具体异常类型、测试替身和集成测试方案随任务确定。

## 尚未决策的事项

- 数据存储技术、文件格式与目录。
- Git CLI 调用细节、超时策略及执行线程安排。
- JavaFX 页面组织、异步刷新与状态管理方式。
- 应用打包、安装和分发方式。

上述事项需由后续 Task 明确；架构变化必须先经 Tech Lead 批准，再更新本文档。不得在代码实现时自行引入框架或替换技术栈。

## 相关文档

- [需求](REQUIREMENTS.md)
- [开发流程](DEVELOPMENT.md)
- [路线图](ROADMAP.md)
